package ph.com.guanzongroup.cas.po.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static ph.com.guanzongroup.cas.po.controller.TestKit.smart;

import java.math.BigDecimal;
import java.sql.Timestamp;
import org.guanzon.appdriver.base.GRiderCAS;
import org.guanzon.appdriver.constant.EditMode;
import org.guanzon.appdriver.constant.UserRight;
import org.junit.Before;
import ph.com.guanzongroup.cas.po.model.Model_PO_Detail;
import ph.com.guanzongroup.cas.po.model.Model_PO_Master;
import ph.com.guanzongroup.cas.po.status.PurchaseOrderStatus;

/** A loaded General PO (READY, status OPEN) with a mocked database, prompt and helpers. */
abstract class ControllerTestBase {

    static final String TRANS_NO = "M00126000001";

    GRiderCAS gr;
    UserPrompt prompt;
    Model_PO_Master master;
    TestGeneralPO po;
    FrameworkStub stub;

    @Before
    public void setUpController() throws Exception {
        gr = mock(GRiderCAS.class);
        when(gr.getUserID()).thenReturn("U001");
        when(gr.getUserLevel()).thenReturn(UserRight.SUPERVISOR);
        when(gr.getBranchCode()).thenReturn("M001");
        when(gr.getServerDate()).thenReturn(Timestamp.valueOf("2026-11-19 10:00:00"));
        when(gr.isMainOffice()).thenReturn(true);

        prompt = mock(UserPrompt.class);
        master = smart(Model_PO_Master.class);
        when(master.getTransactionNo()).thenReturn(TRANS_NO);
        when(master.getTable()).thenReturn("PO_Master");
        when(master.getValue("sTransNox")).thenReturn(TRANS_NO);
        when(master.getTransactionStatus()).thenReturn(PurchaseOrderStatus.OPEN);
        when(master.getIndustryID()).thenReturn("05");
        when(master.getCompanyID()).thenReturn("0001");
        when(master.getCategoryCode()).thenReturn("0002");
        when(master.getBranchCode()).thenReturn("M001");
        when(master.getEditMode()).thenReturn(EditMode.READY);

        po = new TestGeneralPO();
        po.wire(gr, master);
        po.setUserPrompt(prompt);
        po.mode(EditMode.READY);
        po.parent(true); //no user-rights check unless a test asks for it
        stub = po.stub;
    }

    /** A detail row with an item and a quantity. */
    static Model_PO_Detail line(String fsStockId, double fnQuantity, double fnPrice) {
        Model_PO_Detail loRow = smart(Model_PO_Detail.class);
        when(loRow.getStockID()).thenReturn(fsStockId);
        when(loRow.getValue("sStockIDx")).thenReturn(fsStockId);
        when(loRow.getValue("nQuantity")).thenReturn(fnQuantity);
        when(loRow.getQuantity()).thenReturn(fnQuantity);
        when(loRow.getUnitPrice()).thenReturn(BigDecimal.valueOf(fnPrice));
        when(loRow.getEditMode()).thenReturn(EditMode.UPDATE);
        return loRow;
    }
}
