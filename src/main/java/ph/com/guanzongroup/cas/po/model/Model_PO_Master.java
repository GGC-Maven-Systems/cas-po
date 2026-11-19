package ph.com.guanzongroup.cas.po.model;

import java.sql.SQLException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.guanzon.appdriver.agent.services.Model;
import org.guanzon.appdriver.base.GuanzonException;
import org.guanzon.appdriver.base.MiscUtil;
import org.guanzon.appdriver.base.SQLUtil;
import org.guanzon.appdriver.constant.EditMode;
import org.guanzon.appdriver.constant.Logical;
import org.guanzon.cas.client.model.Model_Client_Address;
import org.guanzon.cas.client.model.Model_Client_Institution_Contact;
import org.guanzon.cas.client.model.Model_Client_Master;
import org.guanzon.cas.client.services.ClientModels;
import org.guanzon.cas.inv.model.Model_Inventory;
import org.guanzon.cas.inv.services.InvModels;
import org.guanzon.cas.inv.warehouse.model.Model_Inv_Stock_Request_Master;
import org.guanzon.cas.inv.warehouse.services.InvWarehouseModels;
import org.guanzon.cas.parameter.model.Model_Branch;
import org.guanzon.cas.parameter.model.Model_Category;
import org.guanzon.cas.parameter.model.Model_Company;
import org.guanzon.cas.parameter.model.Model_Industry;
import org.guanzon.cas.parameter.model.Model_Term;
import org.guanzon.cas.parameter.services.ParamModels;
import org.json.simple.JSONObject;
import ph.com.guanzongroup.cas.po.status.PurchaseOrderStatus;

/**
 * PO_Master record.
 *
 * <p>
 * Copied from ph.com.guanzongroup.cas.po.model.Model_PO_Master with the same
 * columns, getters/setters and reference accessors. The reference accessors
 * load lazily through {@link ModelReferences} (reference tables are cached,
 * transaction records are not).</p>
 */
public class Model_PO_Master extends Model {

    //reference records: built on first access, never in initialize()
    private Model_Branch poBranch;
    private Model_Industry poIndustry;
    private Model_Category poCategory;
    private Model_Company poCompany;
    private Model_Term poTerm;
    private Model_Inv_Stock_Request_Master poInvStockMaster;
    private Model_Inventory poInventory;
    private Model_Client_Master poSupplier;
    private Model_Client_Address poSupplierAdress;
    private Model_Client_Institution_Contact poSupplierContactPerson;

    private boolean isSummarized;

    @Override
    public void initialize() {
        try {
            poEntity = MiscUtil.xml2ResultSet(System.getProperty("sys.default.path.metadata") + XML, getTable());

            poEntity.last();
            poEntity.moveToInsertRow();

            MiscUtil.initRowSet(poEntity);

            //default values
            Date ldToday = SQLUtil.toDate(ModelReferences.shortDate(poGRider.getServerDate()), SQLUtil.FORMAT_SHORT_DATE);
            poEntity.updateObject("cProcessd", PurchaseOrderStatus.Processed.NO);
            poEntity.updateObject("cPreOwned", Logical.NO);
            poEntity.updateObject("cWithAddx", Logical.NO);
            poEntity.updateObject("dExpected", ldToday);
            poEntity.updateObject("dTransact", ldToday);
            poEntity.updateObject("sBranchCd", poGRider.getBranchCode());
            poEntity.updateObject("sTermCode", "M0W1004");
            poEntity.updateObject("sDestinat", poGRider.getBranchCode());
            poEntity.updateObject("nDiscount", 0.00);
            poEntity.updateObject("nAddDiscx", 0.0000);
            poEntity.updateObject("nTranTotl", 0.0000);
            poEntity.updateObject("nAmtPaidx", 0.0000);
            poEntity.updateObject("nDPRatexx", 0.00);
            poEntity.updateObject("nAdvAmtxx", 0.0000);
            poEntity.updateObject("nNetTotal", 0.0000);
            poEntity.updateObject("cEmailSnt", Logical.NO);
            poEntity.updateObject("nEmailSnt", 0);
            poEntity.updateObject("cPrintxxx", Logical.NO);
            poEntity.updateString("cTranStat", PurchaseOrderStatus.OPEN);

            poEntity.insertRow();
            poEntity.moveToCurrentRow();
            poEntity.absolute(1);

            ID = "sTransNox";
            pnEditMode = EditMode.UNKNOWN;
        } catch (SQLException e) {
            logInitError(e);
            System.exit(1);
        }
    }

    /** Logs a failed initialize (the log wrapper is often not set on models). */
    private void logInitError(SQLException foException) {
        if (logwrapr != null) {
            logwrapr.severe(foException.getMessage());
        }
        Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Unable to initialize " + getTable(), foException);
    }

    @Override
    public String getNextCode() {
        return MiscUtil.getNextCode(getTable(), ID, true, poGRider.getGConnection().getConnection(), poGRider.getBranchCode());
    }

    // =====================================================================
    // Columns
    // =====================================================================
    public JSONObject setTransactionNo(String transactionNo) {
        return setValue("sTransNox", transactionNo);
    }

    public String getTransactionNo() {
        return (String) getValue("sTransNox");
    }

    public JSONObject setBranchCode(String branchCode) {
        return setValue("sBranchCd", branchCode);
    }

    public String getBranchCode() {
        return (String) getValue("sBranchCd");
    }

    public JSONObject setIndustryID(String industryID) {
        return setValue("sIndstCdx", industryID);
    }

    public String getIndustryID() {
        return (String) getValue("sIndstCdx");
    }

    public JSONObject setCategoryCode(String categoryCode) {
        return setValue("sCategrCd", categoryCode);
    }

    public String getCategoryCode() {
        return (String) getValue("sCategrCd");
    }

    public JSONObject setTransactionDate(Date transactionDate) {
        return setValue("dTransact", transactionDate);
    }

    public Date getTransactionDate() {
        return (Date) getValue("dTransact");
    }

    public JSONObject setCompanyID(String companyID) {
        return setValue("sCompnyID", companyID);
    }

    public String getCompanyID() {
        return (String) getValue("sCompnyID");
    }

    public JSONObject setDestinationID(String destinationID) {
        return setValue("sDestinat", destinationID);
    }

    public String getDestinationID() {
        return (String) getValue("sDestinat");
    }

    public JSONObject setSupplierID(String supplierID) {
        return setValue("sSupplier", supplierID);
    }

    public String getSupplierID() {
        return (String) getValue("sSupplier");
    }

    public JSONObject setAddressID(String addressID) {
        return setValue("sAddrssID", addressID);
    }

    public String getAddressID() {
        return (String) getValue("sAddrssID");
    }

    public JSONObject setContactID(String contactID) {
        return setValue("sContctID", contactID);
    }

    public String getContactID() {
        return (String) getValue("sContctID");
    }

    public JSONObject setReference(String reference) {
        return setValue("sReferNox", reference);
    }

    public String getReference() {
        return (String) getValue("sReferNox");
    }

    public JSONObject setTermCode(String termCode) {
        return setValue("sTermCode", termCode);
    }

    public String getTermCode() {
        return (String) getValue("sTermCode");
    }

    public JSONObject setDiscount(Number discount) {
        return setValue("nDiscount", discount);
    }

    public Number getDiscount() {
        return (Number) getValue("nDiscount");
    }

    public JSONObject setAdditionalDiscount(Number additionalDiscount) {
        return setValue("nAddDiscx", additionalDiscount);
    }

    public Number getAdditionalDiscount() {
        return (Number) getValue("nAddDiscx");
    }

    public JSONObject setTranTotal(Number tranTotal) {
        return setValue("nTranTotl", tranTotal);
    }

    public Number getTranTotal() {
        return (Number) getValue("nTranTotl");
    }

    public JSONObject setAmountPaid(Number amountPaid) {
        return setValue("nAmtPaidx", amountPaid);
    }

    public Number getAmountPaid() {
        return (Number) getValue("nAmtPaidx");
    }

    public JSONObject setWithAdvPaym(boolean isWithAdvPaym) {
        return setValue("cWithAddx", isWithAdvPaym ? Logical.YES : Logical.NO);
    }

    public boolean getWithAdvPaym() {
        return Logical.YES.equals(getValue("cWithAddx"));
    }

    public JSONObject setDownPaymentRatesPercentage(Number downPaymentRatesPercentage) {
        return setValue("nDPRatexx", downPaymentRatesPercentage);
    }

    public Number getDownPaymentRatesPercentage() {
        return (Number) getValue("nDPRatexx");
    }

    public JSONObject setDownPaymentRatesAmount(Number downPaymentRatesAmount) {
        return setValue("nAdvAmtxx", downPaymentRatesAmount);
    }

    public Number getDownPaymentRatesAmount() {
        return (Number) getValue("nAdvAmtxx");
    }

    public JSONObject setNetTotal(Number netTotal) {
        return setValue("nNetTotal", netTotal);
    }

    public Number getNetTotal() {
        return (Number) getValue("nNetTotal");
    }

    public JSONObject setRemarks(String remarks) {
        return setValue("sRemarksx", remarks);
    }

    public String getRemarks() {
        return (String) getValue("sRemarksx");
    }

    public JSONObject setExpectedDate(Date expectedDate) {
        return setValue("dExpected", expectedDate);
    }

    public Date getExpectedDate() {
        return (Date) getValue("dExpected");
    }

    public JSONObject setEmailSent(String emailSent) {
        return setValue("cEmailSnt", emailSent);
    }

    public String getEmailSent() {
        return (String) getValue("cEmailSnt");
    }

    public JSONObject setNoEmailSent(Number noEmailSent) {
        return setValue("nEmailSnt", noEmailSent);
    }

    public Number getNoEmailSent() {
        return (Number) getValue("nEmailSnt");
    }

    public JSONObject setPrint(String print) {
        return setValue("cPrintxxx", print);
    }

    public String getPrint() {
        return (String) getValue("cPrintxxx");
    }

    public JSONObject setEntryNo(int entryNo) {
        return setValue("nEntryNox", entryNo);
    }

    public Number getEntryNo() {
        return (Number) getValue("nEntryNox");
    }

    public JSONObject setInventoryTypeCode(String inventoryTypeCode) {
        return setValue("sInvTypCd", inventoryTypeCode);
    }

    public String getInventoryTypeCode() {
        return (String) getValue("sInvTypCd");
    }

    public JSONObject setPreOwned(boolean isPreOwned) {
        return setValue("cPreOwned", isPreOwned ? Logical.YES : Logical.NO);
    }

    public boolean getPreOwned() {
        return Logical.YES.equals(getValue("cPreOwned"));
    }

    public JSONObject setProcessed(boolean isProcessed) {
        return setValue("cProcessd", isProcessed ? PurchaseOrderStatus.Processed.YES : PurchaseOrderStatus.Processed.NO);
    }

    public boolean getProcessed() {
        return PurchaseOrderStatus.Processed.YES.equals(getValue("cProcessd"));
    }

    public JSONObject setTransactionStatus(String transactionStatus) {
        return setValue("cTranStat", transactionStatus);
    }

    public String getTransactionStatus() {
        return (String) getValue("cTranStat");
    }

    /** Status with a pending letter (A = 1, B = 2, ...) turned back into its code. */
    public String getConvertedTransactionStatus() {
        String lsStatus = (String) getValue("cTranStat");
        if (lsStatus != null && lsStatus.length() == 1 && "ABCDEFGHIJ".contains(lsStatus)) {
            return String.valueOf(lsStatus.charAt(0) - 64);
        }
        return lsStatus;
    }

    public JSONObject setModifyingId(String modifyingId) {
        return setValue("sModified", modifyingId);
    }

    public String getModifyingId() {
        return (String) getValue("sModified");
    }

    public JSONObject setModifiedDate(Date modifiedDate) {
        return setValue("dModified", modifiedDate);
    }

    public Date getModifiedDate() {
        return (Date) getValue("dModified");
    }

    public void setSummarized(boolean summarized) {
        isSummarized = summarized;
    }

    public boolean isSummarized() {
        return isSummarized;
    }

    // =====================================================================
    // Reference records (lazy; see ModelReferences)
    // =====================================================================
    /** Destination branch (sDestinat). */
    public Model_Branch Branch() throws GuanzonException, SQLException {
        if (poBranch == null) {
            poBranch = new ParamModels(poGRider).Branch();
        }
        return ModelReferences.load(poBranch, ModelReferences.text(getValue("sDestinat")), "Branch", Model_Branch::getBranchCode);
    }

    /** Branch that made the PO (sBranchCd). */
    public Model_Branch Branchx() throws GuanzonException, SQLException {
        if (poBranch == null) {
            poBranch = new ParamModels(poGRider).Branch();
        }
        return ModelReferences.load(poBranch, ModelReferences.text(getValue("sBranchCd")), "Branch", Model_Branch::getBranchCode);
    }

    public Model_Industry Industry() throws GuanzonException, SQLException {
        if (poIndustry == null) {
            poIndustry = new ParamModels(poGRider).Industry();
        }
        return ModelReferences.load(poIndustry, ModelReferences.text(getValue("sIndstCdx")), "Industry", Model_Industry::getIndustryId);
    }

    public Model_Category Category() throws GuanzonException, SQLException {
        if (poCategory == null) {
            poCategory = new ParamModels(poGRider).Category();
        }
        return ModelReferences.load(poCategory, ModelReferences.text(getValue("sCategrCd")), "Category", Model_Category::getCategoryId);
    }

    public Model_Company Company() throws GuanzonException, SQLException {
        if (poCompany == null) {
            poCompany = new ParamModels(poGRider).Company();
        }
        return ModelReferences.load(poCompany, ModelReferences.text(getValue("sCompnyID")), "Company", Model_Company::getCompanyId);
    }

    public Model_Term Term() throws GuanzonException, SQLException {
        if (poTerm == null) {
            poTerm = new ParamModels(poGRider).Term();
        }
        return ModelReferences.load(poTerm, ModelReferences.text(getValue("sTermCode")), "Term", Model_Term::getTermId);
    }

    public Model_Client_Master Supplier() throws GuanzonException, SQLException {
        if (poSupplier == null) {
            poSupplier = new ClientModels(poGRider).ClientMaster();
        }
        return ModelReferences.load(poSupplier, ModelReferences.text(getValue("sSupplier")), "Client_Master", Model_Client_Master::getClientId);
    }

    /** NOTE: reads column sClientID, as the original (PO_Master keeps the address in sAddrssID). */
    public Model_Client_Address SupplierAddress() throws GuanzonException, SQLException {
        if (poSupplierAdress == null) {
            poSupplierAdress = new ClientModels(poGRider).ClientAddress();
        }
        return ModelReferences.load(poSupplierAdress, ModelReferences.text(getValue("sClientID")), "Client_Address", Model_Client_Address::getClientId);
    }

    /** NOTE: reads column sClientID, as the original (PO_Master keeps the contact in sContctID). */
    public Model_Client_Institution_Contact SupplierContactPerson() throws GuanzonException, SQLException {
        if (poSupplierContactPerson == null) {
            poSupplierContactPerson = new ClientModels(poGRider).ClientInstitutionContact();
        }
        return ModelReferences.load(poSupplierContactPerson, ModelReferences.text(getValue("sClientID")),
                "Client_Institution_Contact_Person", Model_Client_Institution_Contact::getClientId);
    }

    /** NOTE: opened by this PO's sTransNox, as the original. Not cached (transaction record). */
    public Model_Inv_Stock_Request_Master InvStockRequestMaster() throws GuanzonException, SQLException {
        if (poInvStockMaster == null) {
            poInvStockMaster = new InvWarehouseModels(poGRider).InventoryStockRequestMaster();
        }
        return ModelReferences.load(poInvStockMaster, ModelReferences.text(getValue("sTransNox")), null,
                Model_Inv_Stock_Request_Master::getTransactionNo);
    }

    /** NOTE: reads column sStockIDx, as the original. Not cached (transaction record). */
    public Model_Inventory Inventory() throws GuanzonException, SQLException {
        if (poInventory == null) {
            poInventory = new InvModels(poGRider).Inventory();
        }
        return ModelReferences.load(poInventory, ModelReferences.text(getValue("sStockIDx")), null, Model_Inventory::getStockId);
    }
}
