package ph.com.guanzongroup.cas.po.controller;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.guanzon.appdriver.base.GRiderCAS;
import org.guanzon.appdriver.base.GuanzonException;
import org.guanzon.appdriver.base.MiscUtil;
import org.guanzon.appdriver.base.SQLUtil;
import org.guanzon.appdriver.constant.RecordStatus;
import org.guanzon.cas.client.account.AP_Client_Master;
import org.guanzon.cas.client.services.ClientControllers;
import org.guanzon.cas.inv.Inventory;
import org.guanzon.cas.inv.services.InvControllers;
import org.guanzon.cas.parameter.Branch;
import org.guanzon.cas.parameter.Project;
import org.guanzon.cas.parameter.Term;
import org.guanzon.cas.parameter.services.ParamControllers;
import ph.com.guanzongroup.cas.po.model.Model_PO_Master;
import ph.com.guanzongroup.cas.po.utility.Suggestion;

/**
 * Auto-suggest lists for the searchable fields of a Purchase Order.
 *
 * <p>
 * Each list is a plain read of the top {@link #LIMIT} matches. It never opens
 * the lookup dialog and never changes the PO; picking a row is done with the
 * PO's Select... methods (PurchaseOrder.SelectSupplier, ...), which apply the
 * record exactly like the search does.</p>
 *
 * <p>
 * The queries start from the same browse SQL and filters as the lookups in
 * {@link PurchaseOrderSearch} (active records, the PO's supplier, category,
 * industry and the row's brand), so a suggestion is always a record the
 * lookup would also offer. Codes match from the start (fast on the index),
 * names match anywhere in the text.</p>
 */
public class PurchaseOrderSuggestions {

    /** Most rows a list returns. */
    public static final int LIMIT = 15;

    private final PurchaseOrder poPO;
    private final Supplier<GRiderCAS> poGRider;
    private final Supplier<ParamControllers> poParams;
    private final Supplier<InvControllers> poInventory;
    private final Supplier<ClientControllers> poClients;

    public PurchaseOrderSuggestions(PurchaseOrder foPO, Supplier<GRiderCAS> foGRider, Supplier<ParamControllers> foParams,
            Supplier<InvControllers> foInventory, Supplier<ClientControllers> foClients) {
        poPO = foPO;
        poGRider = foGRider;
        poParams = foParams;
        poInventory = foInventory;
        poClients = foClients;
    }

    private Model_PO_Master master() {
        return poPO.Master();
    }

    // =====================================================================
    // Header
    // =====================================================================
    /** Active suppliers by client id (from the start) or name (anywhere). */
    public List<Suggestion> supplier(String fsText) throws SQLException, GuanzonException {
        if (isBlank(fsText)) {
            return Collections.emptyList();
        }
        AP_Client_Master loSupplier = poClients.get().APClientMaster();
        loSupplier.setRecordStatus(RecordStatus.ACTIVE);
        String lsSQL = MiscUtil.addCondition(loSupplier.getSQ_Browse(),
                "(a.sClientID LIKE " + startsWith(fsText) + " OR b.sCompnyNm LIKE " + contains(fsText) + ")");
        return read(lsSQL + " ORDER BY b.sCompnyNm LIMIT " + LIMIT, "sClientID", "sClientID", "sCompnyNm", "xAddressx");
    }

    /** Active branches (destination) by code or name. */
    public List<Suggestion> destination(String fsText) throws SQLException, GuanzonException {
        if (isBlank(fsText)) {
            return Collections.emptyList();
        }
        Branch loBranch = poParams.get().Branch();
        loBranch.setRecordStatus(RecordStatus.ACTIVE);
        String lsSQL = MiscUtil.addCondition(loBranch.getSQ_Browse(),
                "(sBranchCd LIKE " + startsWith(fsText) + " OR sBranchNm LIKE " + contains(fsText) + ")");
        return read(lsSQL + " ORDER BY sBranchNm LIMIT " + LIMIT, "sBranchCd", "sBranchCd", "sBranchNm", null);
    }

    /** Active terms by code or description. */
    public List<Suggestion> term(String fsText) throws SQLException, GuanzonException {
        if (isBlank(fsText)) {
            return Collections.emptyList();
        }
        Term loTerm = poParams.get().Term();
        loTerm.setRecordStatus("1");
        String lsSQL = MiscUtil.addCondition(loTerm.getSQ_Browse(),
                "(sTermCode LIKE " + startsWith(fsText) + " OR sDescript LIKE " + contains(fsText) + ")");
        return read(lsSQL + " ORDER BY sDescript LIMIT " + LIMIT, "sTermCode", "sTermCode", "sDescript", null);
    }

    /** Active project titles by code or description (stored as the PO reference). */
    public List<Suggestion> project(String fsText) throws SQLException, GuanzonException {
        if (isBlank(fsText)) {
            return Collections.emptyList();
        }
        Project loProject = poParams.get().Project();
        loProject.setRecordStatus("1");
        String lsSQL = MiscUtil.addCondition(loProject.getSQ_Browse(),
                "(sProjCode LIKE " + startsWith(fsText) + " OR sProjDesc LIKE " + contains(fsText) + ")");
        return read(lsSQL + " ORDER BY sProjDesc LIMIT " + LIMIT, "sProjCode", "sProjCode", "sProjDesc", null);
    }

    // =====================================================================
    // Detail (items of a row)
    // =====================================================================
    /**
     * Active items whose barcode starts with the text.
     *
     * @param fbBySupplier limit to the PO's supplier (as Enter / F3; F4 searches all)
     * @param fbByIndustry limit to the PO's industry (false for General Inventory)
     */
    public List<Suggestion> barcode(String fsText, int fnRow, boolean fbBySupplier, boolean fbByIndustry) throws SQLException, GuanzonException {
        return items(fsText, fnRow, fbBySupplier, fbByIndustry, "a.sBarCodex LIKE " + startsWith(fsText), "a.sBarCodex");
    }

    /** Active items whose description contains the text (same filters as {@link #barcode}). */
    public List<Suggestion> description(String fsText, int fnRow, boolean fbBySupplier, boolean fbByIndustry) throws SQLException, GuanzonException {
        return items(fsText, fnRow, fbBySupplier, fbByIndustry, "a.sDescript LIKE " + contains(fsText), "a.sDescript");
    }

    private List<Suggestion> items(String fsText, int fnRow, boolean fbBySupplier, boolean fbByIndustry,
            String fsMatch, String fsOrder) throws SQLException, GuanzonException {
        if (isBlank(fsText) || fnRow < 0 || fnRow >= poPO.getDetailCount()) {
            return Collections.emptyList();
        }
        Inventory loInventory = poInventory.get().Inventory();
        loInventory.setRecordStatus(RecordStatus.ACTIVE);

        //the same filters as PurchaseOrderSearch.inventory
        String lsSQL = loInventory.getSQ_Browse();
        String lsSupplier = emptyToNull(master().getSupplierID());
        if (fbBySupplier && lsSupplier != null) {
            lsSQL = MiscUtil.addCondition(lsSQL, "g.sSupplier = " + SQLUtil.toSQL(lsSupplier));
        }
        String lsBrand = emptyToNull(poPO.Detail(fnRow).getBrandId());
        if (lsBrand != null) {
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sBrandIDx = " + SQLUtil.toSQL(lsBrand));
        }
        String lsIndustry = emptyToNull(master().getIndustryID());
        if (fbByIndustry && lsIndustry != null) {
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sIndstCdx = " + SQLUtil.toSQL(lsIndustry));
        }
        String lsCategory = master().getCategoryCode();
        if (lsCategory != null) {
            lsSQL = MiscUtil.addCondition(lsSQL, "a.sCategCd1 = " + SQLUtil.toSQL(lsCategory));
        }
        lsSQL = MiscUtil.addCondition(lsSQL, fsMatch)
                + " GROUP BY a.sStockIDx ORDER BY " + fsOrder + " LIMIT " + LIMIT;

        List<Suggestion> laList = new ArrayList<>();
        ResultSet loRS = poGRider.get().executeQuery(lsSQL);
        try {
            while (loRS != null && loRS.next()) {
                laList.add(new Suggestion(loRS.getString("sStockIDx"), loRS.getString("sBarCodex"),
                        loRS.getString("sDescript"), loRS.getString("xMeasurNm"),
                        join(loRS.getString("xBrandNme"), loRS.getString("xModelNme"))));
            }
        } finally {
            MiscUtil.close(loRS);
        }
        return laList;
    }

    // =====================================================================
    // Helpers
    // =====================================================================
    private List<Suggestion> read(String fsSQL, String fsId, String fsCode, String fsName, String fsDetail) throws SQLException {
        List<Suggestion> laList = new ArrayList<>();
        ResultSet loRS = poGRider.get().executeQuery(fsSQL);
        try {
            while (loRS != null && loRS.next()) {
                laList.add(new Suggestion(loRS.getString(fsId), loRS.getString(fsCode), loRS.getString(fsName),
                        fsDetail == null ? null : loRS.getString(fsDetail)));
            }
        } finally {
            MiscUtil.close(loRS);
        }
        return laList;
    }

    /** LIKE 'text%' (quoted; a % or _ typed by the user matches itself). */
    static String startsWith(String fsText) {
        return SQLUtil.toSQL(escapeLike(fsText.trim()) + "%");
    }

    /** LIKE '%text%'. */
    static String contains(String fsText) {
        return SQLUtil.toSQL("%" + escapeLike(fsText.trim()) + "%");
    }

    static String escapeLike(String fsText) {
        return fsText.replace("%", "\\%").replace("_", "\\_");
    }

    /** Non-empty values joined with " / ". */
    static String join(String... faValues) {
        StringBuilder loText = new StringBuilder();
        for (String lsValue : faValues) {
            if (lsValue != null && !lsValue.trim().isEmpty()) {
                if (loText.length() > 0) {
                    loText.append(" / ");
                }
                loText.append(lsValue.trim());
            }
        }
        return loText.toString();
    }

    private static boolean isBlank(String fsText) {
        return fsText == null || fsText.trim().isEmpty();
    }

    private static String emptyToNull(String fsValue) {
        return fsValue == null || fsValue.isEmpty() ? null : fsValue;
    }
}
