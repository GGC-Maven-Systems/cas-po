package ph.com.guanzongroup.cas.po.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sql.rowset.CachedRowSet;
import org.guanzon.appdriver.agent.MatrixAuthChecker;
import org.guanzon.appdriver.base.GuanzonException;
import org.guanzon.appdriver.base.MiscUtil;
import org.guanzon.appdriver.base.SQLUtil;
import org.guanzon.appdriver.constant.EditMode;
import org.guanzon.appdriver.constant.RecordStatus;
import org.guanzon.appdriver.constant.UserRight;
import org.guanzon.cas.inv.warehouse.services.InvWarehouseControllers;
import ph.com.guanzongroup.cas.po.model.Model_PO_Master;
import ph.com.guanzongroup.cas.po.services.PurchaseOrderModels;
import ph.com.guanzongroup.cas.poqt.services.QuotationControllers;
import ph.com.guanzongroup.cas.po.status.POStatus;
import ph.com.guanzongroup.cas.po.status.PurchaseOrderStatus;
import ph.com.guanzongroup.cas.po.validator.PurchaseOrderValidator;
import ph.com.guanzongroup.cas.po.validator.PurchaseOrder_MC;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

/**
 * Purchase Order controller for the Motorcycle industry (categories MC and SP).
 *
 * <p>
 * {@link PurchaseOrder} runs the main procedures; this class plugs the
 * Motorcycle work into them and adds:</p>
 * <ul>
 * <li>status and user-level gates for Update, Confirm, Approve, Disapprove,
 * Cancel, Void and Post (PO-BR-014/015/018/019/021/025);</li>
 * <li>defaults of a new PO and the order type (PO-BR-035);</li>
 * <li>source documents: stock requests and PO quotations (PO-BR-001/021);</li>
 * <li>previous price and price-change check (PO-BR-004/012);</li>
 * <li>clearing the lines when the supplier or category changes (PO-US-005);</li>
 * <li>active items only in the model search (PO-BR-006);</li>
 * <li>attachments removable only while editable (PO-BR-037);</li>
 * <li>outstanding quantity, balance, overdue (PO-BR-024/026, PO-FR-017);</li>
 * <li>lists, status history and printing (PO-BR-020/029/038);</li>
 * <li>the supplier's auto-send check on approval (PO-BR-032).</li>
 * </ul>
 *
 * <p>
 * Field rules (required header fields, dates, supplier, line validity, quantity
 * ceilings, direct PO reason, authority matrix) belong in
 * {@link PurchaseOrder_MC}.</p>
 */
public class PurchaseOrderMC extends PurchaseOrder {

    private static final Logger LOG = Logger.getLogger(PurchaseOrderMC.class.getName());

    // PO_Master.cOrdrType
    public static final String ORDER_REGULAR = "0";
    public static final String ORDER_INDENT = "1";
    public static final String ORDER_URGENT = "2";
    public static final String ORDER_SPECIAL = "3";
    public static final String ORDER_BULK = "4";

    /** Category of motorcycle units; auto-email applies to it only. */
    private static final String CATEGORY_MC_UNIT = "MC";

    /** cTranStat set by Finance when the PO is paid. */
    private static final String STATUS_PAID = "6";

    /** Purchase orders loaded by getPurchaseOrder / getConfirmedPurchaseOrder. */
    private List<Model_PO_Master> paPOMaster = new ArrayList<>();

    private PurchaseOrderSourceDocuments poSourceDocuments;
    private PurchaseOrderPrinter poPrinter;

    // =====================================================================
    // Main hooks
    // =====================================================================
    @Override
    protected PurchaseOrderValidator createValidator() {
        return new PurchaseOrder_MC();
    }

    @Override
    protected void initOthers() {
        SourceDocuments().reset();
    }

    /** Stock requests / quotations loaded for the previous PO are dropped. */
    @Override
    protected void resetOthers() {
        SourceDocuments().reset();
    }

    /** Defaults of a new PO (PO-US-003, PO-BR-035). */
    @Override
    public JSONObject initFields() {
        Master().setValue("cOrdrType", ORDER_REGULAR);
        return successJSON();
    }

    @Override
    public void initSQL() {
        SQL_BROWSE = PurchaseOrderQueries.browse();
    }

    /** Check and load the source documents before the status-change transaction. */
    @Override
    protected JSONObject prepareStatusChange(String fsStatus)
            throws SQLException, GuanzonException, CloneNotSupportedException {
        return SourceDocuments().prepare(fsStatus, paDetailRemoved);
    }

    /**
     * Inside the status-change transaction: save the source documents; the PO
     * quotations follow the new status. Cancel and Void release the quantities
     * (PO-BR-019, PO-BR-021).
     */
    @Override
    protected JSONObject processStatusChange(String fsStatus, String fsRemarks) throws Exception {
        JSONObject loJSON = SourceDocuments().save();
        if (!isSuccess(loJSON)) {
            return loJSON;
        }

        switch (fsStatus) {
            case PurchaseOrderStatus.CONFIRMED:
            case PurchaseOrderStatus.CANCELLED:
            case PurchaseOrderStatus.VOID:
            case PurchaseOrderStatus.RETURNED:
                return SourceDocuments().updateQuotationStatus(fsStatus);
            default:
                return successJSON();
        }
    }

    // =====================================================================
    // Saving
    // =====================================================================
    /** Only an editable PO is saved; previous prices and totals are refreshed. */
    @Override
    protected JSONObject prepareIndustrySave() throws SQLException, CloneNotSupportedException, GuanzonException {
        if (getEditMode() == EditMode.UPDATE && !isEditableStatus(Master().getTransactionStatus())) {
            return errorJSON("Only an open purchase order can be modified.");
        }

        JSONObject loJSON = applyPreviousPrices();
        if (!isSuccess(loJSON)) {
            return loJSON;
        }

        recomputeTotals();
        return successJSON();
    }

    /** A non-success result rolls the save back. */
    @Override
    protected JSONObject saveIndustryRecords() throws SQLException, CloneNotSupportedException, GuanzonException {
        return SourceDocuments().save();
    }

    // =====================================================================
    // Status procedures
    // =====================================================================
    /** Only an Open (or Returned) PO can be modified (PO-BR-014, PO-BR-015). */
    @Override
    public JSONObject UpdateTransaction() throws SQLException, GuanzonException {
        JSONObject loJSON = checkStatus("modify",
                PurchaseOrderStatus.OPEN, PurchaseOrderStatus.RETURNED);
        if (!isSuccess(loJSON)) {
            return result(loJSON);
        }
        return super.UpdateTransaction();
    }

    /**
     * Confirms an Open PO and locks it. An encoder needs an approving officer.
     * There is no automatic approval: approval is a separate step by the
     * approving officer.
     */
    @Override
    public JSONObject ConfirmTransaction(String remarks) throws ParseException, SQLException, CloneNotSupportedException, GuanzonException {
        JSONObject loJSON = checkStatus("confirm",
                PurchaseOrderStatus.OPEN, PurchaseOrderStatus.RETURNED,
                pendingCode(PurchaseOrderStatus.CONFIRMED));
        if (!isSuccess(loJSON)) {
            return result(loJSON);
        }

        loJSON = requireSupervisor();
        if (!isSuccess(loJSON)) {
            return result(loJSON);
        }

        loJSON = super.ConfirmTransaction(remarks);
        refreshStatus();
        return result(loJSON);
    }

    /**
     * Approves a Confirmed PO. The preparer cannot approve it unless the
     * preparer is supervisor level or higher (PO-BR-018). Then reminds the
     * user to email the PO when the supplier is set up for auto-sending
     * (PO-BR-032); the email itself is not sent yet.
     */
    @Override
    public JSONObject ApproveTransaction(String remarks) throws ParseException, SQLException, GuanzonException, CloneNotSupportedException {
        JSONObject loJSON = checkStatus("approve",
                PurchaseOrderStatus.CONFIRMED,
                pendingCode(PurchaseOrderStatus.APPROVED));
        if (!isSuccess(loJSON)) {
            return result(loJSON);
        }

        loJSON = checkSelfApproval();
        if (!isSuccess(loJSON)) {
            return result(loJSON);
        }

        loJSON = super.ApproveTransaction(remarks);
        refreshStatus();

        //a pending matrix request is also "success", so check the status
        if (isSuccess(loJSON)
                && PurchaseOrderStatus.APPROVED.equals(Master().getTransactionStatus())
                && CATEGORY_MC_UNIT.equals(Master().getCategoryCode())
                && isSupplierAutoSend()) {
            //TODO PO-BR-032/033: export the PO, email it to the supplier's
            //addresses, set cEmailSnt / nEmailSnt and log failures
            prompt().warning("This supplier is set up for auto-sending, but automatic email is not "
                    + "available yet. Please email the purchase order manually.", "Purchase Order");
        }
        return result(loJSON);
    }

    /**
     * Disapproves a Confirmed PO: it is tagged Cancelled with the approver's
     * remarks and its quantities go back to the sources (PO-BR-019).
     */
    public JSONObject DisapproveTransaction(String remarks) throws ParseException, SQLException, GuanzonException, CloneNotSupportedException {
        JSONObject loJSON = checkStatus("disapprove",
                PurchaseOrderStatus.CONFIRMED,
                pendingCode(PurchaseOrderStatus.APPROVED),
                pendingCode(PurchaseOrderStatus.CANCELLED));
        if (!isSuccess(loJSON)) {
            return result(loJSON);
        }
        if (isBlank(remarks)) {
            return result(errorJSON("Remarks are required to disapprove a purchase order."));
        }

        loJSON = requireSupervisor();
        if (!isSuccess(loJSON)) {
            return result(loJSON);
        }

        //super: the cancel gate below does not apply to a disapproval
        loJSON = super.CancelTransaction("Disapproved: " + remarks.trim());
        refreshStatus();
        if (isSuccess(loJSON)) {
            loJSON = successJSON("Transaction disapproved successfully.");
        }
        return result(loJSON);
    }

    /**
     * Cancels a Confirmed or Approved PO that has no receiving. Needs
     * supervisor level or higher, and remarks (PO-BR-021, PO-BR-022).
     */
    @Override
    public JSONObject CancelTransaction(String remarks) throws ParseException, SQLException, GuanzonException, CloneNotSupportedException {
        JSONObject loJSON = checkStatus("cancel",
                PurchaseOrderStatus.CONFIRMED, PurchaseOrderStatus.APPROVED,
                pendingCode(PurchaseOrderStatus.APPROVED),
                pendingCode(PurchaseOrderStatus.CANCELLED));
        if (!isSuccess(loJSON)) {
            return result(loJSON);
        }
        if (isBlank(remarks)) {
            return result(errorJSON("Remarks are required to cancel a purchase order."));
        }

        loJSON = checkNotReceived();
        if (!isSuccess(loJSON)) {
            return result(loJSON);
        }

        loJSON = requireSupervisor();
        if (!isSuccess(loJSON)) {
            return result(loJSON);
        }

        loJSON = super.CancelTransaction(remarks);
        refreshStatus();
        return result(loJSON);
    }

    /** Only an Open PO can be voided, without approval (PO-BR-021). */
    @Override
    public JSONObject VoidTransaction(String remarks) throws ParseException, SQLException, GuanzonException, CloneNotSupportedException {
        JSONObject loJSON = checkStatus("void",
                PurchaseOrderStatus.OPEN, PurchaseOrderStatus.RETURNED,
                pendingCode(PurchaseOrderStatus.CONFIRMED));
        if (!isSuccess(loJSON)) {
            return result(loJSON);
        }

        loJSON = super.VoidTransaction(remarks);
        refreshStatus();
        return result(loJSON);
    }

    /**
     * Posts an Approved PO on its first use in PO Receiving (PO-BR-025). A PO
     * already Posted or Paid stays as it is, so later deliveries succeed.
     */
    @Override
    public JSONObject PostTransaction(String remarks) throws ParseException, SQLException, GuanzonException, CloneNotSupportedException {
        JSONObject loJSON = checkStatus("post",
                PurchaseOrderStatus.APPROVED, PurchaseOrderStatus.POSTED, STATUS_PAID);
        if (!isSuccess(loJSON)) {
            return result(loJSON);
        }
        if (!PurchaseOrderStatus.APPROVED.equals(Master().getTransactionStatus())) {
            return result(successJSON("Transaction is already posted."));
        }

        loJSON = super.PostTransaction(remarks);
        refreshStatus();
        return result(loJSON);
    }

    @Override
    public JSONObject ReturnTransaction(String remarks) throws ParseException, SQLException, GuanzonException, CloneNotSupportedException {
        JSONObject loJSON = super.ReturnTransaction(remarks);
        if (isSuccess(loJSON)) {
            refreshStatus();
        }
        return result(loJSON);
    }

    /** Void needs the validator and module access only, never the authority matrix. */
    @Override
    protected JSONObject authorizeStatus(String fsStatus, String fsPendingRemarks, String fsEvent,
                                         MatrixAuthChecker[] foCheck)
            throws ParseException, SQLException, GuanzonException, CloneNotSupportedException {
        if (!PurchaseOrderStatus.VOID.equals(fsStatus) || pbWthParent) {
            return super.authorizeStatus(fsStatus, fsPendingRemarks, fsEvent, foCheck);
        }

        foCheck[0] = null;
        JSONObject loJSON = isEntryOkay(fsStatus);
        if (!isSuccess(loJSON)) {
            return loJSON;
        }
        return seekApproval();
    }

    // =====================================================================
    // Status helpers
    // =====================================================================
    /** True when a PO with this status can still be modified. */
    protected boolean isEditableStatus(String fsStatus) {
        return PurchaseOrderStatus.OPEN.equals(fsStatus)
                || PurchaseOrderStatus.RETURNED.equals(fsStatus);
    }

    private static String pendingCode(String fsStatus) {
        return POStatus.of(fsStatus).getPendingCode();
    }

    /** The PO as currently saved in the database. */
    private Model_PO_Master openSaved(JSONObject[] foResult) throws SQLException, GuanzonException {
        Model_PO_Master loSaved = new PurchaseOrderModels(poGRider).PurchaseOrderMaster();
        foResult[0] = loSaved.openRecord(Master().getTransactionNo());
        return loSaved;
    }

    /**
     * A PO must be loaded, unchanged by anyone else since it was opened, and in
     * one of the allowed statuses.
     */
    private JSONObject checkStatus(String fsAction, String... fasAllowed) throws SQLException, GuanzonException {
        if (getEditMode() != EditMode.READY) {
            return errorJSON("No transaction was loaded.");
        }

        JSONObject[] laResult = new JSONObject[1];
        Model_PO_Master loSaved = openSaved(laResult);
        if (!isSuccess(laResult[0])) {
            return laResult[0];
        }

        String lsStatus = loSaved.getTransactionStatus();
        if (!lsStatus.equals(Master().getTransactionStatus())) {
            return errorJSON("The status of this purchase order has changed. Open it again.");
        }
        if (!Arrays.asList(fasAllowed).contains(lsStatus)) {
            return errorJSON("Unable to " + fsAction + " a purchase order with status "
                    + POStatus.label(lsStatus) + ".");
        }
        return successJSON();
    }

    /** Copies the saved status (including a pending letter) to the loaded PO. */
    private void refreshStatus() throws SQLException, GuanzonException {
        JSONObject[] laResult = new JSONObject[1];
        Model_PO_Master loSaved = openSaved(laResult);
        if (isSuccess(laResult[0])) {
            Master().setTransactionStatus(loSaved.getTransactionStatus());
        }
    }

    /** An encoder needs an approving officer of supervisor level or higher. */
    private JSONObject requireSupervisor() throws SQLException, GuanzonException {
        if (poGRider.getUserLevel() > UserRight.ENCODER) {
            return successJSON();
        }

        JSONObject loJSON = prompt().getUserApproval(poGRider);
        if (!isSuccess(loJSON)) {
            return loJSON;
        }
        if (Integer.parseInt(loJSON.get("nUserLevl").toString()) <= UserRight.ENCODER) {
            return errorJSON("User is not an authorized approving officer..");
        }
        setApproving((String) loJSON.get("sUserIDxx"));
        return successJSON();
    }

    /** Blocks an encoder-level user from approving a PO he or she prepared. */
    private JSONObject checkSelfApproval() throws SQLException, GuanzonException {
        if (poGRider.getUserLevel() > UserRight.ENCODER) {
            return successJSON();
        }

        JSONObject loEntry = getEntryBy();
        if (!isSuccess(loEntry)) {
            return loEntry;
        }
        String lsUser = getSysUser(poGRider.getUserID());
        if (lsUser != null && lsUser.equalsIgnoreCase((String) loEntry.get("sCompnyNm"))) {
            return errorJSON("You cannot approve a purchase order that you prepared.");
        }
        return successJSON();
    }

    /** Error when the PO was already used in PO Receiving. */
    private JSONObject checkNotReceived() throws SQLException, GuanzonException {
        JSONObject[] laResult = new JSONObject[1];
        Model_PO_Master loSaved = openSaved(laResult);
        if (!isSuccess(laResult[0])) {
            return laResult[0];
        }
        final String lsError = "This purchase order was already used in PO Receiving. "
                + "Cancel the undelivered quantities through PO Cancellation.";
        if ("1".equals(String.valueOf(loSaved.getValue("cProcessd")))) {
            return errorJSON(lsError);
        }

        String lsSQL = "SELECT sStockIDx FROM PO_Detail"
                + " WHERE sTransNox = " + SQLUtil.toSQL(Master().getTransactionNo())
                + " AND nReceived > 0";
        ResultSet loRS = poGRider.executeQuery(lsSQL);
        if (loRS == null) {
            return errorJSON("Query execution failed.");
        }
        try {
            return loRS.next() ? errorJSON(lsError) : successJSON();
        } finally {
            MiscUtil.close(loRS);
        }
    }

    // =====================================================================
    // Header entry
    // =====================================================================
    /** Order type: 0 Regular, 1 Indent, 2 Urgent, 3 Special Order, 4 Bulk Order. */
    public JSONObject setOrderType(String fsOrderType) {
        JSONObject loJSON = checkEntryMode();
        if (!isSuccess(loJSON)) {
            return result(loJSON);
        }
        if (!Arrays.asList(ORDER_REGULAR, ORDER_INDENT, ORDER_URGENT, ORDER_SPECIAL, ORDER_BULK)
                .contains(fsOrderType)) {
            return result(errorJSON("Invalid order type."));
        }
        Master().setValue("cOrdrType", fsOrderType);
        return result(successJSON());
    }

    /** True while adding or modifying an editable PO. */
    protected JSONObject checkEntryMode() {
        if (getEditMode() != EditMode.ADDNEW && getEditMode() != EditMode.UPDATE) {
            return errorJSON("Invalid edit mode detected.");
        }
        if (getEditMode() == EditMode.UPDATE && !isEditableStatus(Master().getTransactionStatus())) {
            return errorJSON("Only an open purchase order can be modified.");
        }
        return successJSON();
    }

    /**
     * True when a line has an item. The screen can ask the user to confirm
     * before changing the supplier or category, because the lines are cleared.
     */
    public boolean hasItemLines() {
        for (int lnRow = 0; lnRow < getDetailCount(); lnRow++) {
            if (!isBlank(Detail(lnRow).getStockID())) {
                return true;
            }
        }
        return false;
    }

    /** Changing the supplier after lines exist clears them. */
    @Override
    public JSONObject SearchSupplier(String value, boolean byCode) throws SQLException, GuanzonException {
        Object loOld = Master().getValue("sSupplier");
        JSONObject loJSON = super.SearchSupplier(value, byCode);
        if (isSuccess(loJSON)) {
            JSONObject loClear = clearLinesIfChanged(loOld, Master().getValue("sSupplier"), "supplier");
            if (!isSuccess(loClear)) {
                return result(loClear);
            }
        }
        return result(loJSON);
    }

    /** Changing the category after lines exist clears them. */
    @Override
    public JSONObject SearchCategory(String value, boolean byCode) throws SQLException, GuanzonException {
        Object loOld = Master().getCategoryCode();
        JSONObject loJSON = super.SearchCategory(value, byCode);
        if (isSuccess(loJSON)) {
            JSONObject loClear = clearLinesIfChanged(loOld, Master().getCategoryCode(), "category");
            if (!isSuccess(loClear)) {
                return result(loClear);
            }
        }
        return result(loJSON);
    }

    private JSONObject clearLinesIfChanged(Object foOld, Object foNew, String fsField) {
        //only while entering; a first-time selection keeps the lines
        if ((getEditMode() != EditMode.ADDNEW && getEditMode() != EditMode.UPDATE)
                || foOld == null || isBlank(foOld.toString()) || foOld.equals(foNew) || !hasItemLines()) {
            return successJSON();
        }
        try {
            //RemoveDetail keeps track of saved rows and leaves one empty row
            for (int lnRow = getDetailCount() - 1; lnRow >= 0; lnRow--) {
                JSONObject loJSON = RemoveDetail(lnRow);
                if (!isSuccess(loJSON)) {
                    return loJSON;
                }
            }
        } catch (CloneNotSupportedException ex) {
            LOG.log(Level.SEVERE, MiscUtil.getException(ex), ex);
            return errorJSON(MiscUtil.getException(ex));
        }
        SourceDocuments().reset();
        prompt().warning("The " + fsField + " was changed, so the item lines were cleared.", "Purchase Order");
        return successJSON();
    }

    // =====================================================================
    // Detail lines, prices and totals
    // =====================================================================
    /** Removes a line and recomputes the totals. */
    @Override
    public JSONObject RemoveDetail(int row) throws CloneNotSupportedException {
        JSONObject loJSON = super.RemoveDetail(row);
        if (isSuccess(loJSON)) {
            recomputeTotals();
        }
        return result(loJSON);
    }

    /**
     * Total = sum of quantity x unit price (2 decimals), then the net total.
     * VAT and withholding tax are not included yet.
     */
    public void recomputeTotals() {
        BigDecimal ldTotal = BigDecimal.ZERO;
        for (int lnRow = 0; lnRow < getDetailCount(); lnRow++) {
            ldTotal = ldTotal.add(money(Detail(lnRow).getQuantity())
                    .multiply(money(Detail(lnRow).getUnitPrice())));
        }
        Master().setValue("nTranTotl", ldTotal.setScale(2, RoundingMode.HALF_UP).doubleValue());
        computeNetTotal();
    }

    /** Includes only active items (PO-BR-006). */
    @Override
    public JSONObject SearchModel(String value, boolean byCode, int row, boolean hasNoSupplier)
            throws SQLException, GuanzonException {
        JSONObject loJSON = Search().inventory(value, byCode, row, hasNoSupplier, true,
                RecordStatus.ACTIVE, "Barcode: ", true);
        return result(withPreviousPrices(loJSON));
    }

    @Override
    public JSONObject SearchBarcode(String value, boolean byCode, int row, boolean hasNoSupplier)
            throws SQLException, GuanzonException, CloneNotSupportedException {
        return result(withPreviousPrices(super.SearchBarcode(value, byCode, row, hasNoSupplier)));
    }

    @Override
    public JSONObject SearchBarcodeDescription(String value, boolean byCode, int row, boolean hasNoSupplier)
            throws SQLException, GuanzonException, CloneNotSupportedException {
        return result(withPreviousPrices(super.SearchBarcodeDescription(value, byCode, row, hasNoSupplier)));
    }

    /** After a successful item entry, fills the previous prices; keeps the entry's result. */
    private JSONObject withPreviousPrices(JSONObject foResult) throws SQLException {
        if (!isSuccess(foResult)) {
            return foResult;
        }
        JSONObject loJSON = applyPreviousPrices();
        return isSuccess(loJSON) ? foResult : loJSON;
    }

    /**
     * Previous price = unit price of the item's latest Approved, Posted or Paid
     * PO with the same supplier. When there is none, the value already on the
     * line (from the item entry) is kept (PO-BR-012).
     */
    protected JSONObject applyPreviousPrices() throws SQLException {
        Object loSupplier = Master().getValue("sSupplier");
        if (loSupplier == null || isBlank(loSupplier.toString())) {
            return successJSON();
        }

        for (int lnRow = 0; lnRow < getDetailCount(); lnRow++) {
            String lsStockID = Detail(lnRow).getStockID();
            if (isBlank(lsStockID)) {
                continue;
            }

            String lsSQL = "SELECT b.nUnitPrce"
                    + " FROM PO_Master a, PO_Detail b"
                    + " WHERE a.sTransNox = b.sTransNox"
                    + " AND a.sSupplier = " + SQLUtil.toSQL(loSupplier.toString())
                    + " AND b.sStockIDx = " + SQLUtil.toSQL(lsStockID)
                    + " AND a.cTranStat IN ("
                    + SQLUtil.toSQL(PurchaseOrderStatus.APPROVED) + ", "
                    + SQLUtil.toSQL(PurchaseOrderStatus.POSTED) + ", "
                    + SQLUtil.toSQL(STATUS_PAID) + ")"
                    + " AND a.sTransNox <> " + SQLUtil.toSQL(
                    Master().getTransactionNo() == null ? "" : Master().getTransactionNo())
                    + " ORDER BY a.dTransact DESC, a.sTransNox DESC LIMIT 1";
            ResultSet loRS = poGRider.executeQuery(lsSQL);
            if (loRS == null) {
                return errorJSON("Query execution failed.");
            }
            try {
                if (loRS.next()) {
                    Detail(lnRow).setValue("nOldPrice", loRS.getDouble("nUnitPrce"));
                }
            } finally {
                MiscUtil.close(loRS);
            }
        }
        return successJSON();
    }

    /** True when the line's price differs from its previous price, for highlighting (PO-BR-004). */
    public boolean isPriceChanged(int row) {
        BigDecimal ldOld = amount(Detail(row).getValue("nOldPrice"));
        return ldOld.signum() > 0 && ldOld.compareTo(money(Detail(row).getUnitPrice())) != 0;
    }

    /** True when no line comes from a quotation or stock request (PO-BR-005). */
    public boolean isDirectPO() {
        for (int lnRow = 0; lnRow < getDetailCount(); lnRow++) {
            Object loSource = Detail(lnRow).getValue("sSourceNo");
            if (loSource != null && !isBlank(loSource.toString())) {
                return false;
            }
        }
        return true;
    }

    // =====================================================================
    // Fulfillment and payment (read only)
    // =====================================================================
    /** Ordered - (received + cancelled), never negative (PO-BR-024). */
    public double getOutstandingQuantity(int row) {
        BigDecimal ldOutstanding = money(Detail(row).getQuantity())
                .subtract(amount(Detail(row).getValue("nReceived")))
                .subtract(amount(Detail(row).getValue("nCancelld")));
        return ldOutstanding.max(BigDecimal.ZERO).doubleValue();
    }

    /** Approved, with outstanding quantity, past the expected delivery date (PO-FR-017). */
    public boolean isOverdue() throws SQLException, GuanzonException {
        Object loExpected = Master().getValue("dExpected");
        if (!PurchaseOrderStatus.APPROVED.equals(Master().getTransactionStatus())
                || !(loExpected instanceof java.util.Date)) {
            return false;
        }
        if (!toLocalDate((java.util.Date) loExpected).isBefore(toLocalDate(poGRider.getServerDate()))) {
            return false;
        }
        for (int lnRow = 0; lnRow < getDetailCount(); lnRow++) {
            if (getOutstandingQuantity(lnRow) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Net total - amount paid (PO-BR-026). */
    public double getBalance() {
        return amount(Master().getValue("nNetTotal"))
                .subtract(amount(Master().getValue("nAmtPaidx")))
                .doubleValue();
    }

    // =====================================================================
    // Attachments
    // =====================================================================
    /** Saved attachments are removed only while the PO is Open (PO-BR-037). */
    @Override
    public JSONObject removeAttachment(int fnRow) throws GuanzonException, SQLException {
        if (getEditMode() != EditMode.ADDNEW && !isEditableStatus(Master().getTransactionStatus())) {
            return result(errorJSON("Attachments can be removed only while the purchase order is open."));
        }
        return super.removeAttachment(fnRow);
    }

    // =====================================================================
    // Source documents (stock requests, PO quotations)
    // =====================================================================
    public JSONObject getApprovedStockRequests() throws SQLException, GuanzonException {
        return result(SourceDocuments().getAvailableSources());
    }

    public JSONObject addStockRequestOrdersToPODetail(String transactionNo)
            throws CloneNotSupportedException, SQLException, GuanzonException {
        JSONObject loJSON = checkEntryMode();
        if (!isSuccess(loJSON)) {
            return result(loJSON);
        }
        loJSON = withPreviousPrices(SourceDocuments().addStockRequestLines(transactionNo));
        if (isSuccess(loJSON)) {
            recomputeTotals();
        }
        return result(loJSON);
    }

    public JSONObject addPOQuotationToPODetail(String transactionNo)
            throws CloneNotSupportedException, SQLException, GuanzonException {
        JSONObject loJSON = checkEntryMode();
        if (!isSuccess(loJSON)) {
            return result(loJSON);
        }
        loJSON = withPreviousPrices(SourceDocuments().addQuotationLines(transactionNo));
        if (isSuccess(loJSON)) {
            recomputeTotals();
        }
        return result(loJSON);
    }

    // =====================================================================
    // Lists (PO-BR-029)
    // =====================================================================
    /** Browse dialog; opens the picked PO. */
    public JSONObject SearchTransaction(String fsValue, String fsSupplierID, String fsReferID, String fsTransNo, int fnSort)
            throws CloneNotSupportedException, SQLException, GuanzonException {
        initSQL();
        String lsSQL = MiscUtil.addCondition(SQL_BROWSE, String.join(" AND ",
                "a.sIndstCdx = " + SQLUtil.toSQL(Master().getIndustryID()),
                "a.sCompnyID = " + SQLUtil.toSQL(Master().getCompanyID()),
                "a.sSupplier LIKE " + SQLUtil.toSQL("%" + fsSupplierID),
                "a.sCategrCd LIKE " + SQLUtil.toSQL("%" + Master().getCategoryCode()),
                "a.sReferNox LIKE " + SQLUtil.toSQL("%" + fsReferID),
                "a.sTransNox LIKE " + SQLUtil.toSQL("%" + fsTransNo)));
        lsSQL += andIfAny(PurchaseOrderQueries.statusOrPending("a.cTranStat", psTranStat, psTranStat));
        lsSQL += branchFilter("a.sBranchCd");
        lsSQL += " GROUP BY a.sTransNox";
        LOG.log(Level.FINE, lsSQL);

        JSONObject loPicked = prompt().browse(poGRider, lsSQL, fsValue,
                "Transaction Date»Transaction No»Reference No»Company»Supplier",
                "dTransact»sTransNox»a.sReferNox»c.sCompnyNm»e.sCompnyNm",
                "dTransact»sTransNox»a.sReferNox»e.sCompnyNm",
                fnSort);
        if (loPicked == null) {
            return result(errorJSON("No record loaded."));
        }
        return OpenTransaction((String) loPicked.get("sTransNox"));
    }

    /** POs with the statuses in psTranStat (and their pending letters). */
    public JSONObject getPurchaseOrder(String fsSupplierID, String fsReferID, String fsTransactionNo) throws SQLException, GuanzonException {
        return result(loadPurchaseOrders(fsSupplierID, fsReferID, psTranStat));
    }

    /** POs with the statuses in psTranStat, plus POs with approval pending. */
    public JSONObject getConfirmedPurchaseOrder(String fsSupplierID, String fsReferID) throws SQLException, GuanzonException {
        return result(loadPurchaseOrders(fsSupplierID, fsReferID, PurchaseOrderStatus.APPROVED));
    }

    private JSONObject loadPurchaseOrders(String fsSupplierID, String fsReferID, String fsPendingCodes) throws SQLException, GuanzonException {
        String lsSQL = MiscUtil.addCondition(PurchaseOrderQueries.purchaseOrderList(), String.join(" AND ",
                "a.sIndstCdx = " + SQLUtil.toSQL(Master().getIndustryID()),
                "a.sCompnyID = " + SQLUtil.toSQL(Master().getCompanyID()),
                "a.sSupplier LIKE " + SQLUtil.toSQL("%" + fsSupplierID),
                "a.sCategrCd = " + SQLUtil.toSQL(Master().getCategoryCode()),
                "a.sTransNox LIKE " + SQLUtil.toSQL("%" + fsReferID)));
        lsSQL += andIfAny(PurchaseOrderQueries.statusOrPending("a.cTranStat", psTranStat, fsPendingCodes));
        lsSQL += branchFilter("a.sBranchCd");
        //urgent orders first (PO-BR-036)
        lsSQL += " ORDER BY (a.cOrdrType = " + SQLUtil.toSQL(ORDER_URGENT) + ") DESC,"
                + " a.dTransact, e.sCompnyNm, a.sTransNox ASC";
        LOG.log(Level.FINE, lsSQL);

        paPOMaster = new ArrayList<>();
        ResultSet loRS = poGRider.executeQuery(lsSQL);
        if (loRS == null) {
            return errorJSON("Query execution failed.");
        }
        try {
            while (loRS.next()) {
                Model_PO_Master loMaster = new PurchaseOrderModels(poGRider).PurchaseOrderMaster();
                loMaster.openRecord(loRS.getString("sTransNox"));
                paPOMaster.add(loMaster);
            }
        } finally {
            MiscUtil.close(loRS);
        }
        return successJSON("Record loaded successfully.");
    }

    public Model_PO_Master POMaster(int row) {
        return paPOMaster.get(row);
    }

    public int getPOMasterCount() {
        return paPOMaster.size();
    }

    /** " AND " + condition, or empty when there is no condition. */
    private static String andIfAny(String fsCondition) {
        return fsCondition.isEmpty() ? "" : " AND " + fsCondition;
    }

    /** Branches other than main office / warehouse see their own records only. */
    private String branchFilter(String fsColumn) {
        if (poGRider.isMainOffice() || poGRider.isWarehouse()) {
            return "";
        }
        return " AND " + fsColumn + " = " + SQLUtil.toSQL(poGRider.getBranchCode());
    }

    // =====================================================================
    // Status history (PO-BR-038)
    // =====================================================================
    public void ShowStatusHistory() throws Exception {
        CachedRowSet loHistory = getStatusHistory();
        loHistory.beforeFirst();
        while (loHistory.next()) {
            loHistory.updateString("cRefrStat", POStatus.historyLabel(loHistory.getString("cRefrStat")));
            loHistory.updateRow();
        }

        JSONObject loEntry = getEntryBy();
        String lsEntryBy = isSuccess(loEntry) ? (String) loEntry.get("sCompnyNm") : "";
        String lsEntryDate = isSuccess(loEntry) ? (String) loEntry.get("sEntryDte") : "";

        showStatusHistoryUI("Purchase Order", Master().getTransactionNo(), lsEntryBy, lsEntryDate, loHistory);
    }

    // =====================================================================
    // Printing (PO-BR-020)
    // =====================================================================
    /** Only Approved, Posted and Paid POs are printed for the supplier. */
    public JSONObject printTransaction(String jasperType) {
        String lsStatus = Master().getTransactionStatus();
        if (!Arrays.asList(PurchaseOrderStatus.APPROVED, PurchaseOrderStatus.POSTED, STATUS_PAID)
                .contains(lsStatus)) {
            return result(errorJSON("Only approved purchase orders can be printed."));
        }
        return result(Printer().printTransaction(jasperType));
    }

    // =====================================================================
    // Helpers: override a create... method to give an industry its own version
    // =====================================================================
    protected PurchaseOrderSourceDocuments createSourceDocuments() {
        return new PurchaseOrderSourceDocuments(this, poGRider,
                () -> new InvWarehouseControllers(poGRider, logwrapr),
                () -> new QuotationControllers(poGRider, logwrapr), //QUOTATION LINK (cas-poqt)
                this::AddDetail);
    }

    protected PurchaseOrderPrinter createPrinter() {
        return new PurchaseOrderPrinter(this, poGRider, Audit());
    }

    public PurchaseOrderSourceDocuments SourceDocuments() {
        if (poSourceDocuments == null) {
            poSourceDocuments = createSourceDocuments();
        }
        return poSourceDocuments;
    }

    public PurchaseOrderPrinter Printer() {
        if (poPrinter == null) {
            poPrinter = createPrinter();
        }
        return poPrinter;
    }

    // =====================================================================
    // Small helpers
    // =====================================================================
    protected static boolean isBlank(String fsValue) {
        return fsValue == null || fsValue.trim().isEmpty();
    }

    /** Column value as BigDecimal; 0 when empty. */
    private static BigDecimal amount(Object foValue) {
        return foValue instanceof Number ? money((Number) foValue) : BigDecimal.ZERO;
    }

    private static LocalDate toLocalDate(java.util.Date fdDate) {
        return new java.sql.Date(fdDate.getTime()).toLocalDate();
    }

    // =====================================================================
    // Auto-send (Motorcycle only)
    // =====================================================================
    /** AP_Client_Master.cAutoSend of the PO's supplier and category. */
    public boolean isSupplierAutoSend() throws SQLException {
        Object loSupplier = Master().getValue("sSupplier");
        if (loSupplier == null) {
            return false;
        }

        String lsSQL = "SELECT cAutoSend FROM AP_Client_Master"
                + " WHERE sClientID = " + SQLUtil.toSQL(loSupplier.toString())
                + " AND sCategrCd = " + SQLUtil.toSQL(Master().getCategoryCode());
        ResultSet loRS = poGRider.executeQuery(lsSQL);
        if (loRS == null) {
            return false;
        }
        try {
            return loRS.next() && "1".equals(loRS.getString("cAutoSend"));
        } finally {
            MiscUtil.close(loRS);
        }
    }
}