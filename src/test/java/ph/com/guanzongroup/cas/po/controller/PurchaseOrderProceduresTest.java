package ph.com.guanzongroup.cas.po.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ph.com.guanzongroup.cas.po.controller.TestKit.err;
import static ph.com.guanzongroup.cas.po.controller.TestKit.json;
import static ph.com.guanzongroup.cas.po.controller.TestKit.ok;
import static ph.com.guanzongroup.cas.po.controller.TestKit.row;
import static ph.com.guanzongroup.cas.po.controller.TestKit.rows;
import static ph.com.guanzongroup.cas.po.controller.TestKit.smart;

import java.sql.SQLException;
import java.util.Date;
import org.guanzon.appdriver.agent.ActionAuthManager;
import org.guanzon.appdriver.agent.MatrixAuthChecker;
import org.guanzon.appdriver.constant.EditMode;
import org.guanzon.appdriver.constant.UserRight;
import org.guanzon.cas.inv.InventoryTransaction;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Test;
import org.mockito.MockedConstruction;
import ph.com.guanzongroup.cas.po.model.Model_PO_Detail;
import ph.com.guanzongroup.cas.po.model.Model_PO_Master;
import ph.com.guanzongroup.cas.po.services.PurchaseOrderModels;
import ph.com.guanzongroup.cas.po.status.PurchaseOrderStatus;

/** Procedures, status changes and the authorization gate of PurchaseOrder. */
public class PurchaseOrderProceduresTest extends ControllerTestBase {

    private static String result(JSONObject foJSON) {
        return (String) foJSON.get("result");
    }

    @SuppressWarnings("unchecked")
    private static JSONObject withMatrix() {
        JSONArray laMatrix = new JSONArray();
        laMatrix.add(json("sAuthType", "PO1"));
        JSONObject loJSON = ok();
        loJSON.put("matrix", laMatrix);
        return loJSON;
    }

    /** User rights: the current user is authorized. */
    private static MockedConstruction<ActionAuthManager> authorized() {
        return mockConstruction(ActionAuthManager.class, (m, c) -> when(m.isAuthorized()).thenReturn(json("result", "true")));
    }

    // =====================================================================
    // Init / New / Open / Update / Save
    // =====================================================================
    @Test
    public void initTransactionBuildsTheModels() {
        Model_PO_Master loMaster = smart(Model_PO_Master.class);
        Model_PO_Detail loDetail = smart(Model_PO_Detail.class);
        try (MockedConstruction<PurchaseOrderModels> loModels = mockConstruction(PurchaseOrderModels.class, (m, c) -> {
            when(m.PurchaseOrderMaster()).thenReturn(loMaster);
            when(m.PurchaseOrderDetails()).thenReturn(loDetail);
        })) {
            assertEquals("success", result(po.InitTransaction()));
            assertSame(loMaster, po.poMaster);
            assertSame(loDetail, po.poDetail);
            assertEquals(0, po.getDetailCount());
            verify(stub.sourceDocuments).reset(); //General's initOthers
        }
    }

    @Test
    public void newOpenUpdateAndSaveWithParent() throws Exception {
        po.removed().add(line("S1", 1, 1));
        assertEquals("success", result(po.NewTransaction()));
        assertEquals(0, po.getDetailRemovedCount());
        verify(stub.attachments).reset();

        assertEquals("success", result(po.OpenTransaction(TRANS_NO)));
        assertEquals(TRANS_NO, stub.openedNo);

        po.removed().add(line("S1", 1, 1));
        assertEquals("success", result(po.UpdateTransaction()));
        assertEquals(0, po.getDetailRemovedCount());

        assertEquals("success", result(po.SaveTransaction()));
        assertEquals(1, stub.saves);
        assertSame(stub.saveResult, po.lastResult());
    }

    @Test
    public void proceduresCheckTheUserRights() throws Exception {
        po.parent(false);
        try (MockedConstruction<ActionAuthManager> loAuth = authorized()) {
            assertEquals("success", result(po.NewTransaction()));
            assertEquals("success", result(po.OpenTransaction(TRANS_NO)));
            assertEquals("success", result(po.UpdateTransaction()));
            assertEquals(3, loAuth.constructed().size());
        }
    }

    @Test
    public void proceduresStopWhenNotAuthorized() throws Exception {
        po.parent(false);
        when(prompt.getUserApproval(gr)).thenReturn(err("Cancelled"));
        try (MockedConstruction<ActionAuthManager> loAuth = mockConstruction(ActionAuthManager.class,
                (m, c) -> when(m.isAuthorized()).thenReturn(json("result", "false", "warning", "No rights")))) {
            assertEquals("Cancelled", po.NewTransaction().get("message"));
            assertEquals("Cancelled", po.OpenTransaction(TRANS_NO).get("message"));
            assertEquals("Cancelled", po.UpdateTransaction().get("message"));
            verify(prompt, times(3)).warning("No rights", "Authorization Required");
            assertNull(stub.openedNo);
        }
    }

    @Test
    public void seekApprovalWithAnApprovingOfficer() throws Exception {
        when(prompt.getUserApproval(gr)).thenReturn(json("result", "success", "sUserIDxx", "M001", "nUserLevl", "4"));
        try (MockedConstruction<ActionAuthManager> loAuth = mockConstruction(ActionAuthManager.class, (m, c) -> {
            when(m.isAuthorized()).thenReturn(json("result", "false", "warning", "No rights"));
            when(m.isAuthorized("M001", 4)).thenReturn(json("result", "true"));
        })) {
            assertEquals("success", result(po.seekApproval()));
        }
        try (MockedConstruction<ActionAuthManager> loAuth = mockConstruction(ActionAuthManager.class, (m, c) -> {
            when(m.isAuthorized()).thenReturn(json("result", "false", "warning", "No rights"));
            when(m.isAuthorized("M001", 4)).thenReturn(json("result", "false", "warning", "Officer has no rights"));
        })) {
            JSONObject loJSON = po.seekApproval();
            assertEquals("error", result(loJSON));
            assertEquals("User is not an authorized approving officer..", loJSON.get("message"));
            verify(prompt).warning("Officer has no rights", "Authorization Required");
        }
    }

    // =====================================================================
    // Status changes (Confirm, Approve, Cancel, Post, Void)
    // =====================================================================
    @Test
    public void statusChangesNeedALoadedTransaction() throws Exception {
        po.mode(EditMode.UNKNOWN);
        assertEquals("No transaction was loaded.", po.CancelTransaction("x").get("message"));
        assertEquals("No transaction was loaded.", po.ReturnTransaction("x").get("message"));
        assertTrue(stub.statuses.isEmpty());
    }

    @Test
    public void cancelPostVoidWriteTheirStatus() throws Exception {
        assertEquals("Transaction cancelled successfully.", po.CancelTransaction("r1").get("message"));
        assertEquals("Transaction posted successfully.", po.PostTransaction("r2").get("message"));
        assertEquals("Transaction voided successfully.", po.VoidTransaction("r3").get("message"));
        assertEquals("[3, 6, 4]", stub.statuses.toString());
        assertEquals("[r1, r2, r3]", stub.statusRemarks.toString());
        //Post with a parent (the fixture) leaves the DB transaction to the parent
        verify(gr, times(2)).beginTrans(eq("UPDATE STATUS"), anyString(), eq("PO"), eq(TRANS_NO));
        verify(gr, times(2)).commitTrans();
    }

    @Test
    public void postWithAParentDoesNotOpenItsOwnTransaction() throws Exception {
        po.parent(true);
        po.PostTransaction("");
        verify(gr, never()).beginTrans(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void statusChangeStopsOnTheValidator() throws Exception {
        po.parent(false);
        when(stub.validator.validate()).thenReturn(err("Not allowed"));
        assertEquals("Not allowed", po.CancelTransaction("").get("message"));
        assertTrue(stub.statuses.isEmpty());
    }

    @Test
    public void statusChangeWithoutMatrixChecksTheUserRights() throws Exception {
        po.parent(false);
        try (MockedConstruction<ActionAuthManager> loAuth = authorized()) {
            assertEquals("success", result(po.VoidTransaction("")));
            assertEquals(1, loAuth.constructed().size());
        }
    }

    @Test
    public void statusChangeRollsBackWhenTheWriteFails() throws Exception {
        stub.statusResult = err("Write failed");
        assertEquals("Write failed", po.CancelTransaction("").get("message"));
        verify(gr).rollbackTrans();
        verify(gr, never()).commitTrans();
    }

    @Test
    public void statusChangeRollsBackOnException() throws Exception {
        stub.statusError = new SQLException("Connection lost");
        JSONObject loJSON = po.VoidTransaction("");
        assertEquals("error", result(loJSON));
        verify(gr).rollbackTrans();
    }

    @Test
    public void failedRollbackIsOnlyLogged() throws Exception {
        stub.statusResult = err("Write failed");
        doThrow(new SQLException("rollback")).when(gr).rollbackTrans();
        assertEquals("Write failed", po.CancelTransaction("").get("message"));
    }

    @Test
    public void approveRecordsTheInventoryLedger() throws Exception {
        when(master.getValue("dTransact")).thenReturn(new Date());
        when(master.getValue("sIndstCdx")).thenReturn("05");
        po.rows(line("S1", 2, 10), line("S2", 3, 5));
        po.branch("M001");
        try (MockedConstruction<InventoryTransaction> loLedger = mockConstruction(InventoryTransaction.class)) {
            assertEquals("Transaction approved successfully.", po.ApproveTransaction("").get("message"));
            InventoryTransaction loTrans = loLedger.constructed().get(0);
            verify(loTrans).PurchaseOrder(eq(TRANS_NO), any(Date.class), eq(false));
            verify(loTrans, times(2)).addDetail(eq("05"), anyString(), eq("0"), eq(0.0), anyDouble(), anyDouble());
            verify(loTrans).saveTransaction();
        }
        assertEquals("[5]", stub.statuses.toString());
    }

    @Test
    public void cancellingAnApprovedPOReversesTheLedger() throws Exception {
        when(master.getValue("cTranStat")).thenReturn(PurchaseOrderStatus.APPROVED);
        po.rows(line("S1", 2, 10));
        try (MockedConstruction<InventoryTransaction> loLedger = mockConstruction(InventoryTransaction.class)) {
            po.CancelTransaction("");
            verify(loLedger.constructed().get(0)).PurchaseOrder(eq(TRANS_NO), any(), eq(true));
        }
    }

    // =====================================================================
    // Authorization matrix
    // =====================================================================
    @Test
    public void matrixAlreadyApprovedGoesOn() throws Exception {
        po.parent(false);
        when(stub.validator.validate()).thenReturn(withMatrix());
        try (MockedConstruction<MatrixAuthChecker> loMatrix = mockConstruction(MatrixAuthChecker.class, (m, c) -> {
            when(m.loadAuth()).thenReturn(ok());
            when(m.isAuthOkay()).thenReturn(true);
        })) {
            assertEquals("Transaction cancelled successfully.", po.CancelTransaction("").get("message"));
            verify(loMatrix.constructed().get(0)).postAuth();
        }
    }

    @Test
    public void matrixLoadFailureStops() throws Exception {
        po.parent(false);
        when(stub.validator.validate()).thenReturn(withMatrix());
        try (MockedConstruction<MatrixAuthChecker> loMatrix = mockConstruction(MatrixAuthChecker.class,
                (m, c) -> when(m.loadAuth()).thenReturn(err("No matrix")))) {
            assertEquals("No matrix", po.CancelTransaction("").get("message"));
        }
    }

    @Test
    public void matrixApprovedByTheCurrentUser() throws Exception {
        po.parent(false);
        when(stub.validator.validate()).thenReturn(withMatrix());
        try (MockedConstruction<MatrixAuthChecker> loMatrix = mockConstruction(MatrixAuthChecker.class, (m, c) -> {
            when(m.loadAuth()).thenReturn(ok());
            when(m.isAuthOkay()).thenReturn(false, true);
            when(m.isAllowSys()).thenReturn(true);
            when(m.authTrans("PO1", "U001")).thenReturn(ok());
        })) {
            assertEquals("success", result(po.CancelTransaction("")));
            verify(prompt, never()).getUserApproval(any());
        }
    }

    @Test
    public void matrixApprovedByAnApprovingOfficer() throws Exception {
        po.parent(false);
        when(stub.validator.validate()).thenReturn(withMatrix());
        when(prompt.getUserApproval(gr)).thenReturn(json("result", "success", "sUserIDxx", "M001"));
        try (MockedConstruction<MatrixAuthChecker> loMatrix = mockConstruction(MatrixAuthChecker.class, (m, c) -> {
            when(m.loadAuth()).thenReturn(ok());
            when(m.isAuthOkay()).thenReturn(false, true);
            when(m.isAllowSys()).thenReturn(true);
            when(m.authTrans("PO1", "U001")).thenReturn(err("Not your level"));
            when(m.authTrans("PO1", "M001")).thenReturn(ok());
        })) {
            assertEquals("success", result(po.CancelTransaction("")));
            verify(loMatrix.constructed().get(0)).authTrans("PO1", "M001");
        }
    }

    @Test
    public void matrixOfficerCancelledOrNotAllowed() throws Exception {
        po.parent(false);
        when(stub.validator.validate()).thenReturn(withMatrix());
        when(prompt.getUserApproval(gr)).thenReturn(err("Cancelled"), json("result", "success", "sUserIDxx", "M002"));
        try (MockedConstruction<MatrixAuthChecker> loMatrix = mockConstruction(MatrixAuthChecker.class, (m, c) -> {
            when(m.loadAuth()).thenReturn(ok());
            when(m.isAllowSys()).thenReturn(true);
            when(m.authTrans(eq("PO1"), anyString())).thenReturn(err("Not allowed"));
        })) {
            assertEquals("Cancelled", po.CancelTransaction("").get("message"));
            assertEquals("Not allowed", po.CancelTransaction("").get("message"));
            assertTrue(stub.statuses.isEmpty());
        }
    }

    @Test
    public void matrixStillPendingTagsThePendingStatus() throws Exception {
        po.parent(false);
        when(stub.validator.validate()).thenReturn(withMatrix());
        try (MockedConstruction<MatrixAuthChecker> loMatrix = mockConstruction(MatrixAuthChecker.class,
                (m, c) -> when(m.loadAuth()).thenReturn(ok()))) {
            //Approve turns a filed request into success
            JSONObject loJSON = po.ApproveTransaction("");
            assertEquals("success", result(loJSON));
            assertEquals("Transaction approving request submitted successfully.", loJSON.get("message"));
            assertEquals("[E]", stub.statuses.toString()); //64 + 5
            assertEquals("[Approval matrix]", stub.statusRemarks.toString());

            //Cancel returns "matrix"
            assertEquals("matrix", result(po.CancelTransaction("why")));
        }
    }

    // =====================================================================
    // Return, POCancel, RevertStatus
    // =====================================================================
    @Test
    public void returnBySupervisor() throws Exception {
        assertEquals("Transaction returned successfully.", po.ReturnTransaction("fix it").get("message"));
        assertEquals("[9]", stub.statuses.toString());
        verify(stub.sourceDocuments).updateQuotationStatus(PurchaseOrderStatus.RETURNED);
    }

    @Test
    public void returnStopsOnTheValidator() throws Exception {
        when(stub.validator.validate()).thenReturn(err("Cannot return"));
        assertEquals("Cannot return", po.ReturnTransaction("").get("message"));
    }

    @Test
    public void encoderNeedsAnApprovingOfficerToReturn() throws Exception {
        when(gr.getUserLevel()).thenReturn(UserRight.ENCODER);
        when(prompt.getUserApproval(gr)).thenReturn(err("Cancelled"),
                json("result", "success", "sUserIDxx", "E002", "nUserLevl", "1"),
                json("result", "success", "sUserIDxx", "M001", "nUserLevl", "4"));
        assertEquals("Cancelled", po.ReturnTransaction("").get("message"));
        assertEquals("User is not an authorized approving officer..", po.ReturnTransaction("").get("message"));
        assertEquals("success", result(po.ReturnTransaction("")));
        assertEquals("[9]", stub.statuses.toString());
    }

    @Test
    public void returnStopsWhenPreparingOrSavingFails() throws Exception {
        when(stub.sourceDocuments.prepare(anyString(), any())).thenReturn(err("Stock request changed"));
        assertEquals("Stock request changed", po.ReturnTransaction("").get("message"));

        when(stub.sourceDocuments.prepare(anyString(), any())).thenReturn(ok());
        when(stub.sourceDocuments.save()).thenReturn(err("Save failed"));
        assertEquals("Save failed", po.ReturnTransaction("").get("message"));

        when(stub.sourceDocuments.save()).thenReturn(ok());
        stub.statusResult = err("Write failed");
        assertEquals("Write failed", po.ReturnTransaction("").get("message"));
    }

    @Test
    public void poCancelWritesInsideTheParent() throws Exception {
        assertEquals("Transaction cancelled successfully.", po.POCancelTransaction().get("message"));
        assertEquals("[3]", stub.statuses.toString());
        verify(gr, never()).beginTrans(anyString(), anyString(), anyString(), anyString());

        stub.statusResult = err("Write failed");
        assertEquals("Write failed", po.POCancelTransaction().get("message"));

        stub.statusError = new SQLException("Connection lost");
        assertEquals("error", result(po.POCancelTransaction()));
    }

    @Test
    public void revertStatusPutsBackThePreviousStatus() throws Exception {
        when(gr.executeQuery(anyString())).thenReturn(rows(row("cRefrStat", "1")));
        assertEquals("1", po.getPrevStatus());

        when(gr.executeQuery(anyString())).thenReturn(rows(row("cRefrStat", "1")));
        assertEquals("Transaction status reverted successfully.", po.RevertStatus().get("message"));
        assertEquals("[1]", stub.statuses.toString());
    }

    @Test
    public void revertStatusWithNoHistoryDoesNothing() throws Exception {
        when(gr.executeQuery(anyString())).thenReturn(rows());
        assertEquals("success", result(po.RevertStatus()));
        when(gr.executeQuery(anyString())).thenReturn(null);
        assertEquals("", po.getPrevStatus());
        assertTrue(stub.statuses.isEmpty());
    }

    // =====================================================================
    // Default hooks of PurchaseOrder
    // =====================================================================
    @Test
    public void baseControllerDefaults() throws Exception {
        TestBasePO loBase = new TestBasePO();
        loBase.wire(gr, master);
        loBase.setUserPrompt(prompt);
        loBase.mode(EditMode.READY);
        loBase.parent(true);
        try (MockedConstruction<PurchaseOrderModels> loModels = mockConstruction(PurchaseOrderModels.class)) {
            assertEquals("success", result(loBase.InitTransaction()));
        }
        loBase.wire(gr, master);
        assertEquals("success", result(loBase.defaultPrepareStatusChange("1")));
        assertEquals("success", result(loBase.defaultProcessStatusChange("1")));
        assertEquals("success", result(loBase.defaultIndustrySave()));
        assertEquals("success", result(loBase.defaultIndustryRecords()));
        assertEquals("Transaction confirmed successfully.", loBase.ConfirmTransaction("ok").get("message"));
        loBase.mode(EditMode.READY);
        assertEquals("success", result(loBase.ReturnTransaction("")));
        loBase.initSQL();
        assertEquals("SELECT * FROM PO_Master", loBase.sqlBrowse());
        assertEquals("[1, 9]", loBase.stub.statuses.toString());
    }

    @Test
    public void defaultPromptIsTheJavaFxOne() {
        TestBasePO loBase = new TestBasePO();
        assertTrue(loBase.prompt() instanceof FxUserPrompt);
        assertSame(loBase.prompt(), loBase.prompt());
    }

    @Test
    public void inTransactionWithANullResult() {
        JSONObject loJSON = po.inTransaction("Test", true, () -> null);
        assertEquals("Unknown error during Test.", loJSON.get("message"));
    }

    @Test
    public void simpleCallbacks() throws Exception {
        assertEquals("success", result(po.initFields()));
        assertEquals("PO", po.getSourceCode());
        po.removed().add(line("S1", 1, 1));
        po.saveComplete();
        assertEquals(0, po.getDetailRemovedCount());
        when(master.getTransactionStatus()).thenReturn("A");
        assertNotNull(po.getStatusValue());
        po.resetTerm();
        verify(master).setTermCode("");
    }

    @Test
    public void saveRunsTheOpenRules() throws Exception {
        when(stub.validator.validate()).thenReturn(err("Supplier is not set."));
        assertEquals("Supplier is not set.", po.save().get("message"));
        verify(stub.validator).setTransactionStatus(PurchaseOrderStatus.OPEN);
        verify(stub.validator).setApplicationDriver(gr);
        verify(stub.validator).setMaster(master);
        verify(stub.validator).setDetail(any());
        verify(stub.validator).setRemovedDetail(any());
        verify(stub.validator).setActiveAttachmentCount(anyInt());
    }
}
