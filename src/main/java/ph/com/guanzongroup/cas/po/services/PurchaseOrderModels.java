package ph.com.guanzongroup.cas.po.services;

import org.guanzon.appdriver.base.GRiderCAS;
import ph.com.guanzongroup.cas.po.model.Model_PO_Detail;
import ph.com.guanzongroup.cas.po.model.Model_PO_Master;

/**
 * Creates the PO_Master / PO_Detail models (one of each per instance), set up
 * with their metadata (Model_PO_Master.xml / Model_PO_Detail.xml) and table.
 */
public class PurchaseOrderModels {

    private final GRiderCAS poGRider;
    private Model_PO_Master poMaster;
    private Model_PO_Detail poDetail;

    public PurchaseOrderModels(GRiderCAS applicationDriver) {
        poGRider = applicationDriver;
    }

    public Model_PO_Master PurchaseOrderMaster() {
        if (poGRider == null) {
            System.err.println("PurchaseOrderModels.PurchaseOrderMaster: Application driver is not set.");
            return null;
        }
        if (poMaster == null) {
            poMaster = new Model_PO_Master();
            poMaster.setApplicationDriver(poGRider);
            poMaster.setXML("Model_PO_Master");
            poMaster.setTableName("PO_Master");
            poMaster.initialize();
        }
        return poMaster;
    }

    public Model_PO_Detail PurchaseOrderDetails() {
        if (poGRider == null) {
            System.err.println("PurchaseOrderModels.PurchaseOrderDetails: Application driver is not set.");
            return null;
        }
        if (poDetail == null) {
            poDetail = new Model_PO_Detail();
            poDetail.setApplicationDriver(poGRider);
            poDetail.setXML("Model_PO_Detail");
            poDetail.setTableName("PO_Detail");
            poDetail.initialize();
        }
        return poDetail;
    }
}
