package ph.com.guanzongroup.cas.po.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
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

import java.sql.Date;
import java.time.LocalDate;
import javax.sql.rowset.CachedRowSet;
import org.guanzon.appdriver.base.MiscUtil;
import org.guanzon.appdriver.constant.EditMode;
import org.guanzon.appdriver.constant.RecordStatus;
import org.guanzon.appdriver.constant.UserRight;
import org.guanzon.cas.inv.InventoryTransaction;
import org.guanzon.cas.parameter.model.Model_Project;
import org.guanzon.cas.parameter.services.ParamModels;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import ph.com.guanzongroup.cas.po.model.Model_PO_Master;
import ph.com.guanzongroup.cas.po.services.PurchaseOrderModels;
import ph.com.guanzongroup.cas.po.status.PurchaseOrderStatus;
import ph.com.guanzongroup.cas.po.validator.PurchaseOrder_General;

/** What PurchaseOrderGeneral adds: its hooks, Confirm + auto-approve, lists, reports, project title, history. */
public class PurchaseOrderGeneralControllerTest extends ControllerTestBase {

    private static String result(JSONObject foJSON) {
        return (String) foJSON.get("result");
    }

    /** The engineering project model (built by ParamModels). */
    private MockedConstruction<ParamModels> project(Model_Project foProject) {
        return mockConstruction(ParamModels.class, (m, c) -> when(m.Project()).thenReturn(foProject));
    }

    /** Approving writes the inventory ledger (database): mocked. */
    private static MockedConstruction<InventoryTransaction> ledger() {
        return mockConstruction(InventoryTransaction.class);
    }

    // =====================================================================
    // Hooks
    // =====================================================================
    @Test
    public void validatorIsTheGeneralOne() {
        stub.realHelpers = true;
        assertTrue(po.createValidator() instanceof PurchaseOrder_General);
    }

    @Test
    public void newAndOpenResetTheSourceDocuments() throws Exception {
        po.NewTransaction();
        po.OpenTransaction(TRANS_NO);
        verify(stub.sourceDocuments, times(2)).reset();
    }

    @Test
    public void approvingBuildsThePaymentRequest() throws Exception {
        try (MockedConstruction<InventoryTransaction> loLedger = ledger()) {
            assertEquals("success", result(po.ApproveTransaction("")));
        }
        verify(stub.advancePayment).prepare();
        verify(stub.advancePayment).save();
        verify(stub.sourceDocuments).prepare(eq(PurchaseOrderStatus.APPROVED), any());
        verify(stub.sourceDocuments).save();
        verify(stub.sourceDocuments, never()).updateQuotationStatus(anyString()); //not for APPROVED
    }

    @Test
    public void statusChangeStopsOnTheSourceDocumentsOrPayment() throws Exception {
        when(stub.sourceDocuments.prepare(anyString(), any())).thenReturn(err("Stock request changed"));
        assertEquals("Stock request changed", po.ApproveTransaction("").get("message"));

        when(stub.sourceDocuments.prepare(anyString(), any())).thenReturn(ok());
        when(stub.advancePayment.prepare()).thenReturn(err("No payee"));
        assertEquals("No payee", po.ApproveTransaction("").get("message"));

        when(stub.advancePayment.prepare()).thenReturn(ok());
        when(stub.advancePayment.save()).thenReturn(err("Payment not saved"));
        assertEquals("Payment not saved", po.ApproveTransaction("").get("message"));

        when(stub.sourceDocuments.updateQuotationStatus(anyString())).thenReturn(err("Quotation not updated"));
        assertEquals("Quotation not updated", po.VoidTransaction("").get("message"));
        assertTrue(stub.statuses.isEmpty());
    }

    @Test
    public void cancellingUntagsTheProject() throws Exception {
        when(master.getReference()).thenReturn("PRJ01");
        when(stub.validator.isProjectTitleUpdateRequired(PurchaseOrderStatus.CANCELLED, true)).thenReturn(true);
        Model_Project loProject = smart(Model_Project.class);
        try (MockedConstruction<ParamModels> loModels = project(loProject)) {
            assertEquals("success", result(po.CancelTransaction("")));
            verify(loProject).openRecord("PRJ01");
            verify(loProject).updateRecord();
            verify(loProject).isUsed(false);
            verify(loProject).setModifyingId("U001");
            verify(loProject).saveRecord();
            verify(stub.sourceDocuments).updateQuotationStatus(PurchaseOrderStatus.CANCELLED);
        }
    }

    @Test
    public void projectErrorsStopTheChange() throws Exception {
        when(stub.validator.isProjectTitleUpdateRequired(anyString(), anyBoolean())).thenReturn(true);
        Model_Project loProject = smart(Model_Project.class);
        try (MockedConstruction<ParamModels> loModels = project(loProject)) {
            when(loProject.openRecord(any())).thenReturn(err("Project not found"));
            assertEquals("Project not found", po.VoidTransaction("").get("message"));

            when(loProject.openRecord(any())).thenReturn(ok());
            when(loProject.updateRecord()).thenReturn(err("Locked"));
            assertEquals("Locked", po.VoidTransaction("").get("message"));

            when(loProject.updateRecord()).thenReturn(ok());
            when(loProject.isUsed(false)).thenReturn(err("Still used"));
            assertEquals("Still used", po.VoidTransaction("").get("message"));

            when(loProject.isUsed(false)).thenReturn(ok());
            when(loProject.saveRecord()).thenReturn(err("Project not saved"));
            assertEquals("Project not saved", po.VoidTransaction("").get("message"));
            assertTrue(stub.statuses.isEmpty());
        }
    }

    // =====================================================================
    // Confirm + auto-approve
    // =====================================================================
    @Test
    public void confirmingAnOpenPOApprovesIt() throws Exception {
        try (MockedConstruction<InventoryTransaction> loLedger = ledger()) {
            assertEquals("success", result(po.ConfirmTransaction("ok")));
        }
        assertEquals("[1, 5]", stub.statuses.toString());
        verify(master).setTransactionStatus(PurchaseOrderStatus.CONFIRMED);
    }

    @Test
    public void confirmingStopsWhenConfirmFails() throws Exception {
        stub.statusResult = err("Write failed");
        assertEquals("Write failed", po.ConfirmTransaction("").get("message"));
        verify(master, never()).setTransactionStatus(anyString());
    }

    @Test
    public void aConfirmedPOGoesStraightToApproval() throws Exception {
        when(master.getTransactionStatus()).thenReturn(PurchaseOrderStatus.CONFIRMED);
        try (MockedConstruction<InventoryTransaction> loLedger = ledger()) {
            po.ConfirmTransaction("");
        }
        assertEquals("[5]", stub.statuses.toString());
    }

    // =====================================================================
    // Saving
    // =====================================================================
    @Test
    public void savingAConfirmedPOUpdatesItsSourceDocuments() throws Exception {
        when(master.getTransactionStatus()).thenReturn(PurchaseOrderStatus.CONFIRMED);
        assertEquals("success", result(po.willSave()));
        verify(stub.sourceDocuments).prepare(eq(PurchaseOrderStatus.CONFIRMED), any());
    }

    @Test
    public void savingANewPOTagsTheProject() throws Exception {
        when(master.getEditMode()).thenReturn(EditMode.ADDNEW);
        when(stub.validator.isProjectTitleUpdateRequired(PurchaseOrderStatus.OPEN, false)).thenReturn(true);
        Model_Project loProject = smart(Model_Project.class);
        try (MockedConstruction<ParamModels> loModels = project(loProject)) {
            assertEquals("success", result(po.willSave()));
            verify(loProject).isUsed(true);
            assertEquals("success", result(po.saveOthers()));
            verify(loProject).saveRecord();

            when(loProject.saveRecord()).thenReturn(err("Project not saved"));
            assertEquals("Project not saved", po.saveOthers().get("message"));
            verify(gr).rollbackTrans();

            when(loProject.openRecord(any())).thenReturn(err("Project not found"));
            assertEquals("Project not found", po.willSave().get("message"));
        }
    }

    @Test
    public void sourceDocumentsNotSavedRollBack() throws Exception {
        when(stub.sourceDocuments.save()).thenReturn(err("Stock request not saved"));
        assertEquals("Stock request not saved", po.saveOthers().get("message"));
        verify(gr).rollbackTrans();
    }

    // =====================================================================
    // Lists
    // =====================================================================
    @Test
    public void searchTransactionOpensThePickedPO() throws Exception {
        po.tranStat("01");
        when(prompt.browse(eq(gr), anyString(), eq("x"), anyString(), anyString(), anyString(), eq(1)))
                .thenReturn(json("sTransNox", TRANS_NO));
        assertEquals("success", result(po.SearchTransaction("x", "C001", "", "", 1)));
        assertEquals(TRANS_NO, stub.openedNo);
        ArgumentCaptor<String> loSQL = ArgumentCaptor.forClass(String.class);
        verify(prompt).browse(eq(gr), loSQL.capture(), anyString(), anyString(), anyString(), anyString(), anyInt());
        assertTrue(loSQL.getValue(), loSQL.getValue().contains("a.sSupplier LIKE '%C001'"));
        assertTrue(loSQL.getValue(), loSQL.getValue().endsWith(" GROUP BY a.sTransNox"));
        assertTrue(po.sqlBrowse() != null);
    }

    @Test
    public void searchTransactionWithNothingPicked() throws Exception {
        when(gr.isMainOffice()).thenReturn(false);
        when(gr.isWarehouse()).thenReturn(false);
        assertEquals("No record loaded.", po.SearchTransaction("", "", "", "", 1).get("message"));
        ArgumentCaptor<String> loSQL = ArgumentCaptor.forClass(String.class);
        verify(prompt).browse(eq(gr), loSQL.capture(), anyString(), anyString(), anyString(), anyString(), anyInt());
        assertTrue(loSQL.getValue(), loSQL.getValue().contains("a.sBranchCd = 'M001'")); //a branch sees its own POs
    }

    @Test
    public void purchaseOrderListsOpenEachPO() throws Exception {
        po.tranStat("0");
        Model_PO_Master loListed = smart(Model_PO_Master.class);
        when(gr.executeQuery(anyString())).thenReturn(rows(row("sTransNox", "P1"), row("sTransNox", "P2")));
        try (MockedConstruction<PurchaseOrderModels> loModels = mockConstruction(PurchaseOrderModels.class,
                (m, c) -> when(m.PurchaseOrderMaster()).thenReturn(loListed))) {
            assertEquals("success", result(po.getPurchaseOrder("C001", "", "")));
            assertEquals(2, po.getPOMasterCount());
            assertSame(loListed, po.POMaster(1));
            verify(loListed).openRecord("P2");

            when(gr.executeQuery(anyString())).thenReturn(rows(row("sTransNox", "P3")));
            assertEquals("success", result(po.getConfirmedPurchaseOrder("", "")));
            assertEquals(1, po.getPOMasterCount());

            when(gr.executeQuery(anyString())).thenReturn(null);
            assertEquals("Query execution failed.", po.getPurchaseOrder("", "", "").get("message"));
            assertEquals("Query execution failed.", po.getPurchaseOrderList("", "").get("message"));
        }
        assertEquals(0, po.getInvStockRequestCount());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void stockRequestMastersAreNeverLoaded() {
        po.InvStockRequestMaster(0);
    }

    // =====================================================================
    // Delegated helpers
    // =====================================================================
    @Test
    public void sourceDocumentsSearchesAndPaymentAreDelegated() throws Exception {
        po.getApprovedStockRequests();
        verify(stub.sourceDocuments).getAvailableSources();
        po.addStockRequestOrdersToPODetail("R1");
        verify(stub.sourceDocuments).addStockRequestLines("R1");
        po.addPOQuotationToPODetail("Q1");
        verify(stub.sourceDocuments).addQuotationLines("Q1");

        PurchaseOrderSearch s = stub.search;
        po.SearchBarcodeGeneral("x", true, 0, true);
        verify(s).inventory("x", true, 0, true, false, RecordStatus.ACTIVE, "Model: ", false);
        po.SearchBarcodeDescriptionGeneral("x", false, 0, false);
        verify(s).inventory("x", false, 0, false, false, RecordStatus.ACTIVE, "Barcode: ", false);
        po.SearchProject("P");
        verify(s).project("P");
        po.SelectProject("P1");
        verify(s).pickProject("P1");
        po.SearchCategoryReports("c", false);
        verify(s).categoryForReport("c", false);
        po.SearchDestinationReports("d", false);
        verify(s).destinationForReport("d", false);
        po.SearchBranchReports("b", false);
        verify(s).branchForReport("b", false);
        po.SearchSupplierReports("s", false);
        verify(s).supplierForReport("s", false);
        po.SearchPayee("C001");
        verify(stub.advancePayment).searchPayee("C001");
        assertSame(stub.sourceDocuments, po.SourceDocuments());
        assertSame(stub.advancePayment, po.AdvancePayment());
        assertSame(stub.printer, po.Printer());
    }

    @Test
    public void advancePaymentEntries() {
        assertEquals("success", result(po.setAdvancePaymentRate("10")));
        verify(master).setDownPaymentRatesPercentage(10.0);
        when(stub.validator.validateAdvancePaymentAmount(anyDouble())).thenReturn(err("Too much"));
        assertEquals("Too much", po.setAdvancePaymentAmount("99999").get("message"));
        verify(master).setDownPaymentRatesAmount(0.0);
    }

    @Test
    public void realHelpersAreBuiltOnce() {
        stub.realHelpers = true;
        assertTrue(po.SourceDocuments() instanceof PurchaseOrderSourceDocuments);
        assertSame(po.SourceDocuments(), po.SourceDocuments());
        assertTrue(po.AdvancePayment() instanceof PurchaseOrderAdvancePayment);
        assertTrue(po.Printer() instanceof PurchaseOrderPrinter);
        assertSame(po.Printer(), po.Printer());
    }

    @Test
    public void printing() {
        po.printTransaction("0000002");
        verify(stub.printer).printTransaction("0000002");
        JSONArray laData = new JSONArray();
        po.printReports(true, laData);
        verify(stub.printer).printReports(true, laData, null, null);
        po.PrintSummaryReports(null, laData);
        verify(stub.printer).printReports(false, laData, null, null);
        assertEquals(PurchaseOrderPrinter.formatDateToText("2026-11-19"), PurchaseOrderGeneral.formatDateToText("2026-11-19"));
    }

    @Test
    public void projectUsage() throws Exception {
        when(stub.validator.getProjectUsageCount("P1")).thenReturn(2);
        JSONObject loJSON = po.CheckProjectUsage("P1");
        assertEquals("error", result(loJSON));
        assertEquals(2, loJSON.get("count"));
        when(stub.validator.getProjectUsageCount("P2")).thenReturn(1);
        assertEquals("success", result(po.CheckProjectUsage("P2")));
    }

    // =====================================================================
    // Reports
    // =====================================================================
    @Test
    public void summaryReportWithAllFilters() throws Exception {
        po.tranStat("05");
        when(gr.getUserLevel()).thenReturn(UserRight.ENCODER);
        when(gr.executeQuery(anyString())).thenReturn(rows(row("Supplier", "ACME", "Destination", "MAIN", "Branch", "MAIN",
                "sTransNox", TRANS_NO, "sReferNox", "R1", "dTransact", Date.valueOf("2026-11-19"), "Category", "GEN",
                "Term", "30D", "cTranStat", "5", "nTranTotl", 1500.0)));

        JSONObject loJSON = po.RetrieveSummaryReports(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "M001", "M002", "C001", "0002");
        assertEquals("success", result(loJSON));
        JSONObject loRow = (JSONObject) ((JSONArray) loJSON.get("data")).get(0);
        assertEquals("ACME", loRow.get("Supplier"));
        assertEquals(1500.0, loRow.get("Total"));

        ArgumentCaptor<String> loSQL = ArgumentCaptor.forClass(String.class);
        verify(gr).executeQuery(loSQL.capture());
        String lsSQL = loSQL.getValue();
        assertTrue(lsSQL, lsSQL.contains("a.dTransact BETWEEN '2026-11-01 00:00:00' AND '2026-11-30 00:00:00'"));
        assertTrue(lsSQL, lsSQL.contains("a.sBranchCd = 'M001'"));
        assertTrue(lsSQL, lsSQL.contains("a.sDestinat = 'M002'"));
        assertTrue(lsSQL, lsSQL.contains("a.sSupplier = 'C001'"));
        assertTrue(lsSQL, lsSQL.contains("a.sCategrCd = '0002'"));
        assertTrue(lsSQL, lsSQL.contains("a.sTransNox LIKE 'M001%'")); //encoder: own branch

        po.printReports(true, new JSONArray());
        verify(stub.printer).printReports(eq(true), any(), eq("2026-11-01"), eq("2026-11-30"));
    }

    @Test
    public void detailedReportAndEmptyResults() throws Exception {
        when(gr.executeQuery(anyString())).thenReturn(rows(row("Supplier", "ACME", "sTransNox", TRANS_NO, "nQuantity", 2.0,
                "nReceived", 0.0, "nCancelld", 0.0, "nUnitPrce", 10.0, "Total", 20.0)));
        JSONObject loJSON = po.RetriveSummaryDetailedReports(true, null, null, "", null, " ", null);
        assertEquals("success", result(loJSON));
        assertEquals(20.0, ((JSONObject) ((JSONArray) loJSON.get("data")).get(0)).get("Total"));

        when(gr.executeQuery(anyString())).thenReturn(rows());
        loJSON = po.RetriveSummaryReports(false, null, null, null, null, null, null);
        assertEquals("No records found.", loJSON.get("message"));
        assertTrue(((JSONArray) loJSON.get("data")).isEmpty());

        when(gr.executeQuery(anyString())).thenReturn(null);
        assertEquals("Query execution failed.", po.RetrieveSummaryDetailedReports(null, null, null, null, null, null).get("message"));
    }

    // =====================================================================
    // History
    // =====================================================================
    @Test
    public void statusHistoryShowsLabels() throws Exception {
        CachedRowSet loHistory = mock(CachedRowSet.class);
        when(loHistory.next()).thenReturn(true, false);
        when(loHistory.getString("cRefrStat")).thenReturn("5");
        stub.statusHistory = loHistory;
        when(stub.audit.getEntryBy("PO_Master", TRANS_NO)).thenReturn(json("result", "success", "sCompnyNm", "JUAN", "sEntryDte", "2026-11-19"));

        po.ShowStatusHistory();
        verify(loHistory).updateString(eq("cRefrStat"), anyString());
        verify(loHistory).updateRow();
        Object[] loShown = stub.shownHistory.get(0);
        assertEquals("status", loShown[0]);
        assertEquals("JUAN", loShown[2]);

        when(stub.audit.getEntryBy("PO_Master", TRANS_NO)).thenReturn(err("none"));
        when(loHistory.next()).thenReturn(false);
        po.ShowStatusHistory();
        assertEquals("", stub.shownHistory.get(1)[2]);
    }

    @Test
    public void approvalHistoryShowsTheEntry() throws Exception {
        when(master.getValue("nTranTotl")).thenReturn(1500.0);
        stub.approvalHistory = mock(CachedRowSet.class);
        try (MockedStatic<MiscUtil> loMisc = mockStatic(MiscUtil.class, CALLS_REAL_METHODS)) {
            loMisc.when(() -> MiscUtil.getEntryBy(gr, "PO_Master", TRANS_NO))
                    .thenReturn(json("result", "success", "sCompnyNm", "JUAN", "sEntryDte", "2026-11-19"), err("none"));
            po.ShowApprovalHistory();
            po.ShowApprovalHistory();
        }
        assertEquals("approval", stub.shownHistory.get(0)[0]);
        assertEquals("JUAN", stub.shownHistory.get(0)[2]);
        assertEquals(1500.0, stub.shownHistory.get(0)[4]);
        assertEquals("", stub.shownHistory.get(1)[3]);
        assertFalse(stub.shownHistory.isEmpty());
    }
}
