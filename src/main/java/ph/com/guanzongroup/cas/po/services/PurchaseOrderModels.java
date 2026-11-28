package ph.com.guanzongroup.cas.po.services;

import org.guanzon.appdriver.base.GRiderCAS;
import org.guanzon.cas.parameter.model.Model_Supplier_Email;
import ph.com.guanzongroup.cas.po.model.Model_PO_Detail;
import ph.com.guanzongroup.cas.po.model.Model_PO_Master;
import ph.com.guanzongroup.cas.po.model.Model_Send_Mail_Master;

/**
 * Creates the PO_Master / PO_Detail models (one of each per instance), set up
 * with their metadata (Model_PO_Master.xml / Model_PO_Detail.xml) and table.
 */
public class PurchaseOrderModels {

    private final GRiderCAS poGRider;
    private Model_PO_Master poMaster;
    private Model_PO_Detail poDetail;
    private Model_Send_Mail_Master poSendMailMaster;

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
    public Model_Send_Mail_Master SendMailMaster() {
        if (poGRider == null) {
            System.err.println("PurchaseOrderModels.SendMailMaster: Application driver is not set.");
            return null;
        }

        if (poSendMailMaster == null) {
            poSendMailMaster = new Model_Send_Mail_Master();
            poSendMailMaster.setApplicationDriver(poGRider);
            poSendMailMaster.setXML("Model_Send_Mail_Master");
            poSendMailMaster.setTableName("Send_Mail_Master");
            poSendMailMaster.initialize();
        }

        return poSendMailMaster;
    }
}
