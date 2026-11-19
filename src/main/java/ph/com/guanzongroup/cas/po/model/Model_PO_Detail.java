package ph.com.guanzongroup.cas.po.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.guanzon.appdriver.agent.services.Model;
import org.guanzon.appdriver.agent.services.ReferenceCache;
import org.guanzon.appdriver.base.GuanzonException;
import org.guanzon.appdriver.base.MiscUtil;
import org.guanzon.appdriver.base.SQLUtil;
import org.guanzon.appdriver.constant.EditMode;
import org.guanzon.cas.inv.model.Model_Inv_Master;
import org.guanzon.cas.inv.model.Model_Inventory;
import org.guanzon.cas.inv.services.InvModels;
import org.guanzon.cas.inv.warehouse.model.Model_Inv_Stock_Request_Detail;
import org.guanzon.cas.inv.warehouse.model.Model_Inv_Stock_Request_Master;
import org.guanzon.cas.inv.warehouse.services.InvWarehouseModels;
import org.guanzon.cas.parameter.model.Model_Branch;
import org.guanzon.cas.parameter.model.Model_Brand;
import org.guanzon.cas.parameter.model.Model_Category;
import org.guanzon.cas.parameter.model.Model_Color;
import org.guanzon.cas.parameter.model.Model_Company;
import org.guanzon.cas.parameter.model.Model_Industry;
import org.guanzon.cas.parameter.model.Model_Inv_Type;
import org.guanzon.cas.parameter.model.Model_Measure;
import org.guanzon.cas.parameter.model.Model_Model;
import org.guanzon.cas.parameter.model.Model_Term;
import org.guanzon.cas.parameter.services.ParamModels;
//Quotations belong to cas-poqt (see QUOTATION LINK below)
import ph.com.guanzongroup.cas.poqt.model.Model_PO_Quotation_Detail;
import ph.com.guanzongroup.cas.poqt.model.Model_PO_Quotation_Master;
import ph.com.guanzongroup.cas.poqt.services.QuotationModels;
import org.json.simple.JSONObject;

/**
 * PO_Detail record.
 *
 * <p>
 * Copied from ph.com.guanzongroup.cas.po.model.Model_PO_Detail with the same
 * columns, getters/setters and reference accessors. The reference accessors
 * load lazily through {@link ModelReferences} (reference tables are cached,
 * transaction records are not).</p>
 *
 * <p>
 * QUOTATION LINK: {@link #POQuotationMaster()} / {@link #POQuotationDetail()}
 * return cas-poqt's quotation models; they are the only part of this
 * class that uses cas-poqt.</p>
 */
public class Model_PO_Detail extends Model {

    //reference records: built on first access, never in initialize()
    private Model_Branch poBranch;
    private Model_Industry poIndustry;
    private Model_Company poCompany;
    private Model_Term poTerm;
    private Model_Brand poBrand;
    private Model_Model poModel;
    private Model_Color poColor;
    private Model_Category poCategory;
    private Model_Inv_Type poInv_Type;
    private Model_Measure poMeasure;
    private Model_Inv_Stock_Request_Master poInvStockMaster;
    private Model_Inv_Stock_Request_Detail poInvStockDetail;
    private Model_Inventory poInventory;
    private Model_Inv_Master poInventoryMaster;
    private Model_PO_Quotation_Master poPOQuotationMaster;
    private Model_PO_Quotation_Detail poPOQuotationDetail;

    /** Brand filter of the item search (not a column). */
    private String lsBrand;

    @Override
    public void initialize() {
        try {
            poEntity = MiscUtil.xml2ResultSet(System.getProperty("sys.default.path.metadata") + XML, getTable());

            poEntity.last();
            poEntity.moveToInsertRow();

            MiscUtil.initRowSet(poEntity);

            //default values
            poEntity.updateObject("nEntryNox", 0);
            poEntity.updateObject("nCancelld", 0.00);
            poEntity.updateObject("nUnitPrce", 0.0000);
            poEntity.updateObject("nOldPrice", 0.0000);
            poEntity.updateObject("nQtyOnHnd", 0.00);
            poEntity.updateObject("nRecOrder", 0.00);
            poEntity.updateObject("nQuantity", 0.00);
            poEntity.updateObject("nReceived", 0.00);
            poEntity.updateObject("dModified",
                    SQLUtil.toDate(ModelReferences.shortDate(poGRider.getServerDate()), SQLUtil.FORMAT_SHORT_DATE));

            poEntity.insertRow();
            poEntity.moveToCurrentRow();
            poEntity.absolute(1);

            ID = "sTransNox";
            ID2 = "nEntryNox";
            pnEditMode = EditMode.UNKNOWN;
        } catch (SQLException e) {
            if (logwrapr != null) {
                logwrapr.severe(e.getMessage());
            }
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Unable to initialize " + getTable(), e);
            System.exit(1);
        }
    }

    @Override
    public String getNextCode() {
        return "";
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

    public JSONObject setEntryNo(Number entryNo) {
        return setValue("nEntryNox", entryNo);
    }

    public Number getEntryNo() {
        return (Number) getValue("nEntryNox");
    }

    public JSONObject setStockID(String stockID) {
        return setValue("sStockIDx", stockID);
    }

    public String getStockID() {
        return (String) getValue("sStockIDx");
    }

    public JSONObject setDescription(String description) {
        return setValue("sDescript", description);
    }

    public String getDescription() {
        return (String) getValue("sDescript");
    }

    public JSONObject setOldPrice(Number oldPrice) {
        return setValue("nOldPrice", oldPrice);
    }

    public Number getOldPrice() {
        return (Number) getValue("nOldPrice");
    }

    public JSONObject setUnitPrice(Number unitPrice) {
        return setValue("nUnitPrce", unitPrice);
    }

    public Number getUnitPrice() {
        return (Number) getValue("nUnitPrce");
    }

    public JSONObject setQuantityOnHand(Number quantityOnHand) {
        return setValue("nQtyOnHnd", quantityOnHand);
    }

    public Number getQuantityOnHand() {
        return (Number) getValue("nQtyOnHnd");
    }

    public JSONObject setRecordOrder(Number recordOrder) {
        return setValue("nRecOrder", recordOrder);
    }

    public Number getRecordOrder() {
        return (Number) getValue("nRecOrder");
    }

    public JSONObject setQuantity(Number quantity) {
        return setValue("nQuantity", quantity);
    }

    public Number getQuantity() {
        return (Number) getValue("nQuantity");
    }

    public JSONObject setReceivedQuantity(Number receivedQuantity) {
        return setValue("nReceived", receivedQuantity);
    }

    public Number getReceivedQuantity() {
        return (Number) getValue("nReceived");
    }

    public JSONObject setCancelledQuantity(Number cancelledQuantity) {
        return setValue("nCancelld", cancelledQuantity);
    }

    public Number getCancelledQuantity() {
        return (Number) getValue("nCancelld");
    }

    /** Source document code (sSourceCd); the method name keeps the original spelling. */
    public JSONObject setSouceCode(String sourceCode) {
        return setValue("sSourceCd", sourceCode);
    }

    public String getSouceCode() {
        return (String) getValue("sSourceCd");
    }

    /** Source document number (sSourceNo); the method name keeps the original spelling. */
    public JSONObject setSouceNo(String sourceNo) {
        return setValue("sSourceNo", sourceNo);
    }

    public String getSouceNo() {
        return (String) getValue("sSourceNo");
    }

    public JSONObject setModifiedDate(Date modifiedDate) {
        return setValue("dModified", modifiedDate);
    }

    public Date getModifiedDate() {
        return (Date) getValue("dModified");
    }

    /** Brand filter of the item search (kept in memory, not saved). */
    public String setBrandId(String brandID) {
        return lsBrand = brandID;
    }

    public String getBrandId() {
        return lsBrand;
    }

    public JSONObject setSourceEntryNo(Number entryNo) {
        return setValue("nSrEtryNo", entryNo);
    }

    public Number getSourceEntryNo() {
        return (Number) getValue("nSrEtryNo");
    }

    // =====================================================================
    // Reference records (lazy; see ModelReferences)
    // =====================================================================
    public Model_Branch Branch() throws GuanzonException, SQLException {
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

    /** NOTE: reads column sCompanyID, as the original. */
    public Model_Company Company() throws GuanzonException, SQLException {
        if (poCompany == null) {
            poCompany = new ParamModels(poGRider).Company();
        }
        return ModelReferences.load(poCompany, ModelReferences.text(getValue("sCompanyID")), "Company", Model_Company::getCompanyId);
    }

    public Model_Term Term() throws GuanzonException, SQLException {
        if (poTerm == null) {
            poTerm = new ParamModels(poGRider).Term();
        }
        return ModelReferences.load(poTerm, ModelReferences.text(getValue("sTermCode")), "Term", Model_Term::getTermId);
    }

    /** Not cached (transaction record). */
    public Model_Inventory Inventory() throws GuanzonException, SQLException {
        if (poInventory == null) {
            poInventory = new InvModels(poGRider).Inventory();
        }
        return ModelReferences.load(poInventory, ModelReferences.text(getValue("sStockIDx")), null, Model_Inventory::getStockId);
    }

    /** Not cached (transaction record). */
    public Model_Inv_Master InventoryMaster() throws GuanzonException, SQLException {
        if (poInventoryMaster == null) {
            poInventoryMaster = new InvModels(poGRider).InventoryMaster();
        }
        return ModelReferences.load(poInventoryMaster, ModelReferences.text(getValue("sStockIDx")), null, Model_Inv_Master::getStockId);
    }

    /** Brand of the search filter (setBrandId), not a column. */
    public Model_Brand Brand() throws SQLException, GuanzonException {
        if (poBrand == null) {
            poBrand = new ParamModels(poGRider).Brand();
        }
        if (lsBrand == null || lsBrand.isEmpty()) {
            poBrand.initialize();
            return poBrand;
        }
        if (poBrand.getEditMode() == EditMode.READY && poBrand.getBrandId().equals(lsBrand)) {
            return poBrand;
        }
        if (ReferenceCache.tryLoad("Brand", lsBrand, poBrand)) {
            return poBrand;
        }
        JSONObject loJSON = poBrand.openRecord(lsBrand);
        if ("success".equals(loJSON.get("result"))) {
            ReferenceCache.store("Brand", lsBrand, poBrand);
        } else {
            poBrand.initialize();
        }
        return poBrand;
    }

    public Model_Color Color() throws GuanzonException, SQLException {
        if (poColor == null) {
            poColor = new ParamModels(poGRider).Color();
        }
        return ModelReferences.load(poColor, ModelReferences.text(getValue("sColorIDx")), "Color", Model_Color::getColorId);
    }

    public Model_Inv_Type InventoryType() throws GuanzonException, SQLException {
        if (poInv_Type == null) {
            poInv_Type = new ParamModels(poGRider).InventoryType();
        }
        return ModelReferences.load(poInv_Type, ModelReferences.text(getValue("sInvTypCd")), "Inv_Type", Model_Inv_Type::getInventoryTypeId);
    }

    public Model_Measure Measure() throws GuanzonException, SQLException {
        if (poMeasure == null) {
            poMeasure = new ParamModels(poGRider).Measurement();
        }
        return ModelReferences.load(poMeasure, ModelReferences.text(getValue("sMeasurID")), "Measure", Model_Measure::getMeasureId);
    }

    public Model_Model Model() throws GuanzonException, SQLException {
        if (poModel == null) {
            poModel = new ParamModels(poGRider).Model();
        }
        return ModelReferences.load(poModel, ModelReferences.text(getValue("sModelIDx")), "Model", Model_Model::getModelId);
    }

    /** NOTE: opened by this line's sTransNox, as the original. Not cached (transaction record). */
    public Model_Inv_Stock_Request_Master InvStockRequestMaster() throws GuanzonException, SQLException {
        if (poInvStockMaster == null) {
            poInvStockMaster = new InvWarehouseModels(poGRider).InventoryStockRequestMaster();
        }
        return ModelReferences.load(poInvStockMaster, ModelReferences.text(getValue("sTransNox")), null,
                Model_Inv_Stock_Request_Master::getTransactionNo);
    }

    /** Stock request line of this PO line (sSourceNo + sStockIDx). Not cached (transaction record). */
    public Model_Inv_Stock_Request_Detail InvStockRequestDetail() throws GuanzonException, SQLException {
        if (poInvStockDetail == null) {
            poInvStockDetail = new InvWarehouseModels(poGRider).InventoryStockRequestDetail();
        }
        return ModelReferences.load(poInvStockDetail, ModelReferences.text(getValue("sSourceNo")), null,
                Model_Inv_Stock_Request_Detail::getTransactionNo,
                (loModel, lsId) -> loModel.openRecordByReference(lsId, getValue("sStockIDx")));
    }

    /** QUOTATION LINK (cas-poqt). Not cached (transaction record). */
    public Model_PO_Quotation_Master POQuotationMaster() throws GuanzonException, SQLException {
        if (poPOQuotationMaster == null) {
            poPOQuotationMaster = new QuotationModels(poGRider).POQuotationMaster();
        }
        return ModelReferences.load(poPOQuotationMaster, ModelReferences.text(getValue("sSourceNo")), null,
                Model_PO_Quotation_Master::getTransactionNo);
    }

    /** QUOTATION LINK (cas-poqt): quotation line of this PO line. Not cached (transaction record). */
    public Model_PO_Quotation_Detail POQuotationDetail() throws GuanzonException, SQLException {
        if (poPOQuotationDetail == null) {
            poPOQuotationDetail = new QuotationModels(poGRider).POQuotationDetails();
        }
        return ModelReferences.load(poPOQuotationDetail, ModelReferences.text(getValue("sSourceNo")), null,
                Model_PO_Quotation_Detail::getTransactionNo,
                (loModel, lsId) -> loModel.openRecord(lsId, (String) getValue("sStockIDx")));
    }

    /** Opens the line of a PO (Id1) for an item (Id2). */
    public JSONObject openRecordByReference(String Id1, Object Id2) throws SQLException, GuanzonException {
        String lsSQL = MiscUtil.addCondition(MiscUtil.makeSelect(this),
                "sTransNox = " + SQLUtil.toSQL(Id1) + " AND sStockIDx = " + SQLUtil.toSQL(Id2));

        poJSON = new JSONObject();
        ResultSet loRS = poGRider.executeQuery(lsSQL);
        try {
            if (loRS.next()) {
                for (int lnCtr = 1; lnCtr <= loRS.getMetaData().getColumnCount(); lnCtr++) {
                    setValue(lnCtr, loRS.getObject(lnCtr));
                }
                pnEditMode = EditMode.READY;
                poJSON.put("result", "success");
                poJSON.put("message", "Record loaded successfully.");
            } else {
                poJSON.put("result", "error");
                poJSON.put("message", "No record to load.");
            }
        } catch (SQLException e) {
            poJSON.put("result", "error");
            poJSON.put("message", e.getMessage());
        } finally {
            MiscUtil.close(loRS);
        }
        return poJSON;
    }
}
