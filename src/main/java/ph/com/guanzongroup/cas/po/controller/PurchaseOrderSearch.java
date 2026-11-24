package ph.com.guanzongroup.cas.po.controller;

import static ph.com.guanzongroup.cas.po.utility.Results.isSuccess;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.guanzon.appdriver.base.GuanzonException;
import org.guanzon.appdriver.constant.RecordStatus;
import org.guanzon.cas.client.account.AP_Client_Master;
import org.guanzon.cas.client.model.Model_AP_Client_Master;
import org.guanzon.cas.client.services.ClientControllers;
import org.guanzon.cas.inv.Inventory;
import org.guanzon.cas.inv.model.Model_Inventory;
import org.guanzon.cas.inv.services.InvControllers;
import org.guanzon.cas.parameter.Branch;
import org.guanzon.cas.parameter.Brand;
import org.guanzon.cas.parameter.Category;
import org.guanzon.cas.parameter.Company;
import org.guanzon.cas.parameter.Department;
import org.guanzon.cas.parameter.Industry;
import org.guanzon.cas.parameter.Project;
import org.guanzon.cas.parameter.Term;
import org.guanzon.cas.parameter.services.ParamControllers;
import ph.com.guanzongroup.cas.po.model.Model_PO_Detail;
import ph.com.guanzongroup.cas.po.model.Model_PO_Master;
import ph.com.guanzongroup.cas.po.utility.Results;
import org.json.simple.JSONObject;

/**
 * Lookups that fill the Purchase Order: branch, supplier, term, items, ...
 *
 * <p>
 * Each search opens the lookup dialog of its parameter and, when a record is
 * picked, copies it into the PO. Works with any {@link PurchaseOrder}
 * controller; the ...ForReport searches only return the picked record.</p>
 *
 * <p>
 * The pick... methods load a record by its key (a row picked from an
 * auto-suggest list, see {@link PurchaseOrderSuggestions}) and copy it into
 * the PO the same way, without opening the dialog.</p>
 */
public class PurchaseOrderSearch {

    private final PurchaseOrder poPO;
    private final Supplier<ParamControllers> poParams;
    private final Supplier<InvControllers> poInventory;
    private final Supplier<ClientControllers> poClients;

    public PurchaseOrderSearch(PurchaseOrder foPO, Supplier<ParamControllers> foParams,
            Supplier<InvControllers> foInventory, Supplier<ClientControllers> foClients) {
        poPO = foPO;
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
    public JSONObject branch(String fsValue, boolean fbByCode) throws SQLException, GuanzonException {
        Branch loBranch = poParams.get().Branch();
        loBranch.setRecordStatus(RecordStatus.ACTIVE);
        JSONObject loJSON = loBranch.searchRecord(fsValue, fbByCode);
        if (isSuccess(loJSON)) {
            master().setBranchCode(loBranch.getModel().getBranchCode());
        }
        return loJSON;
    }

    public JSONObject industry(String fsValue, boolean fbByCode) throws SQLException, GuanzonException {
        Industry loIndustry = poParams.get().Industry();
        loIndustry.setRecordStatus(RecordStatus.ACTIVE);
        JSONObject loJSON = loIndustry.searchRecord(fsValue, fbByCode);
        if (isSuccess(loJSON)) {
            master().setIndustryID(loIndustry.getModel().getIndustryId());
        }
        return loJSON;
    }

    public JSONObject category(String fsValue, boolean fbByCode) throws SQLException, GuanzonException {
        Category loCategory = poParams.get().Category();
        loCategory.setRecordStatus(RecordStatus.ACTIVE);
        JSONObject loJSON = loCategory.searchRecord(fsValue, fbByCode);
        if (isSuccess(loJSON)) {
            master().setCategoryCode(loCategory.getModel().getCategoryId());
        }
        return loJSON;
    }

    public JSONObject term(String fsValue, boolean fbByCode) throws SQLException, GuanzonException {
        Term loTerm = poParams.get().Term();
        loTerm.setRecordStatus("1");
        JSONObject loJSON = loTerm.searchRecord(fsValue, fbByCode);
        if (isSuccess(loJSON)) {
            master().setTermCode(loTerm.getModel().getTermId());
        }
        return loJSON;
    }

    /** Term by code (auto-suggest pick). */
    public JSONObject pickTerm(String fsTermCode) throws SQLException, GuanzonException {
        Term loTerm = poParams.get().Term();
        JSONObject loJSON = loTerm.getModel().openRecord(fsTermCode);
        if (isSuccess(loJSON)) {
            master().setTermCode(loTerm.getModel().getTermId());
        }
        return loJSON;
    }

    /** Supplier, with its address, contact and (when set) its default term. */
    public JSONObject supplier(String fsValue, boolean fbByCode) throws SQLException, GuanzonException {
        AP_Client_Master loSupplier = poClients.get().APClientMaster();
        loSupplier.setRecordStatus(RecordStatus.ACTIVE);
        JSONObject loJSON = loSupplier.searchRecord(fsValue, fbByCode);
        if (isSuccess(loJSON)) {
            applySupplier(loSupplier.getModel());
        }
        return loJSON;
    }

    /** Supplier by client id (auto-suggest pick). */
    public JSONObject pickSupplier(String fsClientId) throws SQLException, GuanzonException {
        AP_Client_Master loSupplier = poClients.get().APClientMaster();
        JSONObject loJSON = loSupplier.getModel().openRecord(fsClientId);
        if (isSuccess(loJSON)) {
            applySupplier(loSupplier.getModel());
        }
        return loJSON;
    }

    private void applySupplier(Model_AP_Client_Master foSupplier) throws SQLException, GuanzonException {
        master().setSupplierID(foSupplier.getClientId());
        master().setAddressID(foSupplier.ClientAddress().getAddressId());
        master().setContactID(foSupplier.ClientInstitutionContact().getContactPId());
        String lsTerm = foSupplier.getTermId();
        if (lsTerm != null && !lsTerm.isEmpty()) {
            master().setTermCode(lsTerm);
        }
    }

    /** Project title, stored as the PO reference. */
    public JSONObject project(String fsValue) throws SQLException, GuanzonException {
        Project loProject = poParams.get().Project();
        loProject.setRecordStatus("1");
        JSONObject loJSON = loProject.searchRecord(fsValue, true);
        if (isSuccess(loJSON)) {
            master().setReference(loProject.getModel().getProjectID());
        }
        return loJSON;
    }

    /** Project title by code (auto-suggest pick). */
    public JSONObject pickProject(String fsProjectCode) throws SQLException, GuanzonException {
        Project loProject = poParams.get().Project();
        JSONObject loJSON = loProject.getModel().openRecord(fsProjectCode);
        if (isSuccess(loJSON)) {
            master().setReference(loProject.getModel().getProjectID());
        }
        return loJSON;
    }

    public JSONObject company(String fsValue, boolean fbByCode) throws SQLException, GuanzonException {
        Company loCompany = poParams.get().Company();
        loCompany.setRecordStatus(RecordStatus.ACTIVE);
        JSONObject loJSON = loCompany.searchRecord(fsValue, fbByCode);
        if (isSuccess(loJSON)) {
            master().setCompanyID(loCompany.getModel().getCompanyId());
        }
        return loJSON;
    }

    /** Destination branch. */
    public JSONObject destination(String fsValue, boolean fbByCode) throws SQLException, GuanzonException {
        Branch loBranch = poParams.get().Branch();
        loBranch.setRecordStatus(RecordStatus.ACTIVE);
        JSONObject loJSON = loBranch.searchRecord(fsValue, fbByCode);
        if (isSuccess(loJSON)) {
            master().setDestinationID(loBranch.getModel().getBranchCode());
        }
        return loJSON;
    }

    /** Destination branch by code (auto-suggest pick). */
    public JSONObject pickDestination(String fsBranchCode) throws SQLException, GuanzonException {
        Branch loBranch = poParams.get().Branch();
        JSONObject loJSON = loBranch.getModel().openRecord(fsBranchCode);
        if (isSuccess(loJSON)) {
            master().setDestinationID(loBranch.getModel().getBranchCode());
        }
        return loJSON;
    }

    /** Department as destination. */
    public JSONObject department(String fsValue, boolean fbByCode) throws SQLException, GuanzonException {
        Department loDepartment = poParams.get().Department();
        loDepartment.setRecordStatus(RecordStatus.ACTIVE);
        JSONObject loJSON = loDepartment.searchRecord(fsValue, fbByCode);
        if (isSuccess(loJSON)) {
            master().setDestinationID(loDepartment.getModel().getDepartmentId());
        }
        return loJSON;
    }

    // =====================================================================
    // Detail
    // =====================================================================
    public JSONObject brand(String fsValue, boolean fbByCode, int fnRow) throws SQLException, GuanzonException {
        Brand loBrand = poParams.get().Brand();
        loBrand.setRecordStatus(RecordStatus.ACTIVE);
        JSONObject loJSON = loBrand.searchRecord(fsValue, fbByCode, master().getIndustryID());
        if (isSuccess(loJSON)) {
            poPO.Detail(fnRow).setBrandId((String) loJSON.get("sBrandIDx"));
        }
        return loJSON;
    }

    /**
     * Item lookup for a detail row: puts the item and its cost on the row.
     * An item may appear only once among manual lines (rule in the validator).
     *
     * @param fbBySupplier limit to the PO's supplier
     * @param fbByIndustry limit to the PO's industry (false for the "General" variants)
     * @param fsRecordStatus inventory record statuses to include
     * @param fsLabel prefix of the duplicate message ("Model: " / "Barcode: ")
     * @param fbSetBrand also copy the item's brand to the row
     */
    public JSONObject inventory(String fsValue, boolean fbByCode, int fnRow, boolean fbBySupplier,
            boolean fbByIndustry, String fsRecordStatus, String fsLabel, boolean fbSetBrand)
            throws SQLException, GuanzonException {
        Model_PO_Detail loRow = poPO.Detail(fnRow);
        Inventory loInventory = poInventory.get().Inventory();
        loInventory.setRecordStatus(fsRecordStatus);

        String lsSupplier = emptyToNull(master().getSupplierID());
        String lsBrand = emptyToNull(loRow.getBrandId());
        String lsIndustry = fbByIndustry ? emptyToNull(master().getIndustryID()) : null;

        JSONObject loJSON = loInventory.searchRecord(fsValue, fbByCode,
                fbBySupplier ? lsSupplier : null, lsBrand, lsIndustry, master().getCategoryCode());
        if (!isSuccess(loJSON)) {
            return loJSON;
        }

        JSONObject loApply = applyInventory(loRow, fnRow, loInventory.getModel(), fsLabel, fbSetBrand);
        return isSuccess(loApply) ? loJSON : loApply;
    }

    /**
     * Item by stock id for a detail row (auto-suggest pick); the same checks
     * and copying as {@link #inventory}.
     */
    public JSONObject pickInventory(String fsStockId, int fnRow, String fsLabel, boolean fbSetBrand)
            throws SQLException, GuanzonException {
        Model_PO_Detail loRow = poPO.Detail(fnRow);
        Inventory loInventory = poInventory.get().Inventory();
        JSONObject loJSON = loInventory.getModel().openRecord(fsStockId);
        if (!isSuccess(loJSON)) {
            return loJSON;
        }
        return applyInventory(loRow, fnRow, loInventory.getModel(), fsLabel, fbSetBrand);
    }

    private JSONObject applyInventory(Model_PO_Detail foRow, int fnRow, Model_Inventory foItem, String fsLabel,
            boolean fbSetBrand) throws SQLException, GuanzonException {
        String lsStockId = foItem.getStockId();
        JSONObject loUnique = poPO.rules().validateUniqueItem(fnRow, lsStockId, fsLabel + foItem.getDescription());
        if (!isSuccess(loUnique)) {
            return loUnique;
        }

        if (fbSetBrand) {
            foRow.setBrandId(foItem.getBrandId());
        }
        foRow.setStockID(lsStockId);
        foRow.setUnitPrice(foItem.getCost().doubleValue());
        foRow.setOldPrice(foItem.getCost().doubleValue());
        return Results.success();
    }

    // =====================================================================
    // Report filters (return the picked record only)
    // =====================================================================
    public JSONObject categoryForReport(String fsValue, boolean fbByCode) throws SQLException, GuanzonException {
        Category loCategory = poParams.get().Category();
        loCategory.setRecordStatus(RecordStatus.ACTIVE);
        JSONObject loJSON = loCategory.searchRecord(fsValue, fbByCode);
        if (isSuccess(loJSON)) {
            loJSON.put("category", loCategory.getModel().getDescription());
            loJSON.put("categoryID", loCategory.getModel().getCategoryId());
        }
        return loJSON;
    }

    public JSONObject destinationForReport(String fsValue, boolean fbByCode) throws SQLException, GuanzonException {
        Branch loBranch = poParams.get().Branch();
        loBranch.setRecordStatus(RecordStatus.ACTIVE);
        JSONObject loJSON = loBranch.searchRecord(fsValue, fbByCode);
        if (isSuccess(loJSON)) {
            loJSON.put("destination", loBranch.getModel().getBranchName());
            loJSON.put("destinationID", loBranch.getModel().getBranchCode());
        }
        return loJSON;
    }

    public JSONObject branchForReport(String fsValue, boolean fbByCode) throws SQLException, GuanzonException {
        Branch loBranch = poParams.get().Branch();
        loBranch.setRecordStatus(RecordStatus.ACTIVE);
        JSONObject loJSON = loBranch.searchRecord(fsValue, fbByCode);
        if (isSuccess(loJSON)) {
            loJSON.put("branch", loBranch.getModel().getBranchName());
            loJSON.put("branchID", loBranch.getModel().getBranchCode());
        }
        return loJSON;
    }

    public JSONObject supplierForReport(String fsValue, boolean fbByCode) throws SQLException, GuanzonException {
        AP_Client_Master loSupplier = poClients.get().APClientMaster();
        loSupplier.setRecordStatus(RecordStatus.ACTIVE);
        JSONObject loJSON = loSupplier.searchRecord(fsValue, fbByCode);
        if (isSuccess(loJSON)) {
            loJSON.put("supplier", loSupplier.getModel().Client().getCompanyName());
            loJSON.put("supplierID", loSupplier.getModel().Client().getClientId());
        }
        return loJSON;
    }

    // =====================================================================
    // Helpers
    // =====================================================================
    private static String emptyToNull(String fsValue) {
        return fsValue == null || fsValue.isEmpty() ? null : fsValue;
    }

}
