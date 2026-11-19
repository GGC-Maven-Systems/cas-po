package ph.com.guanzongroup.cas.po.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ph.com.guanzongroup.cas.po.controller.TestKit.err;
import static ph.com.guanzongroup.cas.po.controller.TestKit.json;
import static ph.com.guanzongroup.cas.po.controller.TestKit.ok;
import static ph.com.guanzongroup.cas.po.controller.TestKit.row;
import static ph.com.guanzongroup.cas.po.controller.TestKit.rows;
import static ph.com.guanzongroup.cas.po.controller.TestKit.smart;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.guanzon.appdriver.agent.systables.TransactionAttachment;
import org.guanzon.appdriver.constant.EditMode;
import org.guanzon.appdriver.constant.RecordStatus;
import org.guanzon.appdriver.constant.UserRight;
import org.json.simple.JSONObject;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import ph.com.guanzongroup.cas.po.model.Model_PO_Detail;
import ph.com.guanzongroup.cas.po.model.Model_PO_Master;
import ph.com.guanzongroup.cas.po.services.PurchaseOrderModels;
import ph.com.guanzongroup.cas.po.status.PurchaseOrderStatus;
import ph.com.guanzongroup.cas.po.validator.PurchaseOrderValidator;

/** Detail grid, discounts, the save template, attachments, audit, lookups and searches of PurchaseOrder. */
public class PurchaseOrderEditingTest extends ControllerTestBase {

    private static String result(JSONObject foJSON) {
        return (String) foJSON.get("result");
    }

    // =====================================================================
    // Detail grid
    // =====================================================================
    @Test
    public void addDetailFollowsTheValidator() throws Exception {
        when(stub.validator.validateCanAddDetail()).thenReturn(err("Last row has no item."));
        assertEquals("Last row has no item.", po.AddDetail().get("message"));
        assertEquals(0, po.getDetailCount());

        when(stub.validator.validateCanAddDetail()).thenReturn(ok());
        assertEquals("success", result(po.AddDetail()));
        assertEquals(1, po.getDetailCount());
    }

    @Test
    public void removingTheLastRowLeavesAnEmptyRow() throws Exception {
        po.mode(EditMode.UPDATE);
        Model_PO_Detail loSaved = line("S1", 1, 10);
        po.rows(loSaved);
        assertEquals("success", result(po.RemoveDetail(0)));
        assertEquals(1, po.getDetailCount());
        assertSame(loSaved, po.DetailRemove(0));

        po.rows(line("S2", 1, 10));
        stub.addDetailResult = err("Cannot add");
        assertEquals("Cannot add", po.RemoveDetail(0).get("message"));
    }

    @Test
    public void quantitiesAndStatusLabel() {
        when(stub.validator.validateQuantities()).thenReturn(json("result", "success", "warning", "true"));
        assertEquals("true", po.isDetailHasZeroQty().get("warning"));
    }

    // =====================================================================
    // Discounts and totals
    // =====================================================================
    @Test
    public void discountRateIsSetAndTheNetTotalComputed() {
        when(master.getTranTotal()).thenReturn(BigDecimal.valueOf(1000));
        when(master.getDiscount()).thenReturn(10.0);
        when(master.getAdditionalDiscount()).thenReturn(50.0);

        assertEquals("success", result(po.setDiscountRate("1,0")));
        verify(master).setDiscount(10.0);
        ArgumentCaptor<Number> loNet = ArgumentCaptor.forClass(Number.class);
        verify(master, org.mockito.Mockito.atLeastOnce()).setNetTotal(loNet.capture());
        assertEquals(850.0, loNet.getValue().doubleValue(), 0.0001); //1000 - 10% - 50
    }

    @Test
    public void invalidOrRejectedAmountsGoBackToZero() {
        assertEquals("Invalid amount: abc", po.setDiscountAmount("abc").get("message"));
        verify(master).setAdditionalDiscount(0.0);

        when(stub.validator.validateDiscountRate(anyDouble())).thenReturn(err("Rate too high"));
        assertEquals("Rate too high", po.setDiscountRate("120").get("message"));
        verify(master).setDiscount(0.0);

        when(stub.validator.validateNetTotal()).thenReturn(err("Net below zero"));
        assertEquals("Net below zero", po.setDiscountAmount("5000").get("message"));

        when(stub.validator.validateNetTotal()).thenReturn(ok());
        assertEquals("success", result(po.setDiscountAmount("  ")));
    }

    @Test
    public void netTotalCheckerResetsTheQuantity() {
        Model_PO_Detail loRow = line("S1", 3, 100);
        po.rows(loRow);
        assertEquals("success", result(po.netTotalChecker(0)));
        verify(stub.validator).validateNetTotalWithLine(300.0);

        when(stub.validator.validateNetTotalWithLine(anyDouble())).thenReturn(err("Over the limit"));
        assertEquals("Over the limit", po.netTotalChecker(0).get("message"));
        verify(loRow).setQuantity(0);
    }

    @Test
    public void moneyHandlesNullsAndDecimals() {
        assertEquals(BigDecimal.ZERO, PurchaseOrder.money(null));
        BigDecimal ldValue = new BigDecimal("1.25");
        assertSame(ldValue, PurchaseOrder.money(ldValue));
        assertEquals(0, BigDecimal.valueOf(2.5).compareTo(PurchaseOrder.money(2.5)));
    }

    // =====================================================================
    // Save template
    // =====================================================================
    @Test
    public void willSavePreparesTheRowsAndAttachments() throws Exception {
        when(master.getEditMode()).thenReturn(EditMode.ADDNEW);
        when(master.getNextCode()).thenReturn("M00126000009");
        Model_PO_Detail loGood = line("S1", 2, 10);
        Model_PO_Detail loNoItem = line("", 2, 10);
        Model_PO_Detail loNoQty = line("S3", 0, 10);
        Model_PO_Detail loNull = smart(Model_PO_Detail.class);
        po.rows(loGood, loNoItem, loNoQty, loNull);

        assertEquals("success", result(po.willSave()));
        verify(master).setTransactionNo("M00126000009");
        assertEquals(1, po.getDetailCount());
        verify(loGood).setEntryNo(1);
        verify(loGood).setTransactionNo(TRANS_NO);
        verify(stub.attachments).prepareForSave(TRANS_NO, "M001");
    }

    @Test
    public void encoderEditingAConfirmedPONeedsAnOfficer() throws Exception {
        po.mode(EditMode.UPDATE);
        when(master.getTransactionStatus()).thenReturn(PurchaseOrderStatus.CONFIRMED);
        when(gr.getUserLevel()).thenReturn(UserRight.ENCODER);
        when(prompt.getUserApproval(gr)).thenReturn(err("Cancelled"), json("result", "success", "sUserIDxx", "M001"));

        assertEquals("Cancelled", po.willSave().get("message"));
        verify(stub.attachments, never()).prepareForSave(anyString(), anyString());

        assertEquals("success", result(po.willSave()));
        assertEquals("M001", po.psApproved);
    }

    @Test
    public void returnedPOMustHaveAChange() throws Exception {
        when(master.getTransactionStatus()).thenReturn(PurchaseOrderStatus.RETURNED);
        Model_PO_Master loSaved = smart(Model_PO_Master.class);
        when(gr.executeQuery(anyString())).thenReturn(rows(row("sStockIDx", "S1", "nQuantity", 2.0)));
        when(stub.validator.validateReturnedEditLines(any(), any())).thenReturn(err("No changes"));
        try (MockedConstruction<PurchaseOrderModels> loModels = mockConstruction(PurchaseOrderModels.class,
                (m, c) -> when(m.PurchaseOrderMaster()).thenReturn(loSaved))) {
            assertEquals("No changes", po.willSave().get("message"));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<PurchaseOrderValidator.SavedLine>> loLines = ArgumentCaptor.forClass(List.class);
            verify(stub.validator).validateReturnedEditLines(eq(loSaved), loLines.capture());
            assertEquals("S1", loLines.getValue().get(0).getStockId());
            assertEquals(2.0, loLines.getValue().get(0).getQuantity(), 0);

            when(loSaved.openRecord(TRANS_NO)).thenReturn(err("Not found"));
            assertEquals("Not found", po.willSave().get("message"));

            when(loSaved.openRecord(TRANS_NO)).thenReturn(ok());
            when(gr.executeQuery(anyString())).thenReturn(null);
            assertEquals("Query execution failed.", po.willSave().get("message"));
        }
    }

    @Test
    public void industryPreparationFailureStopsTheSave() throws Exception {
        when(master.getTransactionStatus()).thenReturn(PurchaseOrderStatus.CONFIRMED);
        when(stub.sourceDocuments.prepare(anyString(), any())).thenReturn(err("Stock request changed"));
        assertEquals("Stock request changed", po.willSave().get("message"));
    }

    @Test
    public void saveOthersSavesAttachmentsThenIndustryRecords() throws Exception {
        assertEquals("success", result(po.saveOthers()));
        verify(stub.attachments).save();
        verify(stub.sourceDocuments).save();

        when(stub.attachments.save()).thenReturn(err("Upload failed"));
        assertEquals("Upload failed", po.saveOthers().get("message"));

        when(stub.attachments.save()).thenThrow(new SQLException("Connection lost"));
        assertEquals("Connection lost", po.saveOthers().get("message"));
    }

    // =====================================================================
    // Attachments, audit, lookups
    // =====================================================================
    @Test
    public void attachmentsAreDelegated() throws Exception {
        TransactionAttachment loAttachment = smart(TransactionAttachment.class);
        when(stub.attachments.get(0)).thenReturn(loAttachment);
        when(stub.attachments.getCount()).thenReturn(2);
        when(stub.attachments.add("a.png", TRANS_NO)).thenReturn(1);
        when(stub.attachments.isFileNameUsed("used.png")).thenReturn(true);

        po.resetattachment();
        verify(stub.attachments).reset();
        assertSame(loAttachment, po.TransactionAttachmentList(0));
        assertEquals(2, po.getTransactionAttachmentCount());
        assertEquals("success", result(po.addAttachment()));
        assertEquals(1, po.addAttachment("a.png"));
        assertEquals("success", result(po.removeAttachment(0)));
        po.copyFile("C:/a.png");
        verify(stub.attachments).copyToTemp("C:/a.png");
        assertEquals("success", result(po.loadAttachments()));
        verify(stub.attachments).load(TRANS_NO, false);
        assertEquals("success", result(po.uploadCASAttachments(gr, "x", 0, "a.png")));
        verify(stub.attachments).upload(0, "a.png");
        assertEquals("error", result(po.checkExistingFileName("used.png")));
        assertEquals("success", result(po.checkExistingFileName("new.png")));
        assertSame(stub.attachments, po.Attachments());
    }

    @Test
    public void auditIsDelegated() throws Exception {
        when(stub.audit.getApprovers(TRANS_NO)).thenReturn(Arrays.asList("A", "B"));
        when(stub.audit.getSysUserName("U001")).thenReturn("JUAN");
        when(stub.audit.getEntryBy("PO_Master", TRANS_NO)).thenReturn(json("result", "success", "sCompnyNm", "JUAN"));
        assertEquals(Arrays.asList("A", "B"), po.getApprover());
        assertEquals("JUAN", po.getSysUser("U001"));
        assertEquals("JUAN", po.getEntryBy().get("sCompnyNm"));
        assertEquals("success", result(po.getConfirmedBy()));
        assertSame(stub.audit, po.Audit());
    }

    @Test
    public void inventoryTypeOfIndustryAndCategory() throws Exception {
        when(gr.executeQuery(anyString())).thenReturn(rows(row("sInvTypCd", "0007")));
        assertEquals("0007", po.getInventoryTypeCode());
        when(gr.executeQuery(anyString())).thenReturn(rows());
        assertNull(po.getInventoryTypeCode());
        when(gr.executeQuery(anyString())).thenReturn(null);
        assertNull(po.getInventoryTypeCode());
    }

    @Test
    public void realHelpersAreBuiltOnce() {
        stub.realHelpers = true;
        assertTrue(po.Attachments() instanceof PurchaseOrderAttachments);
        assertSame(po.Attachments(), po.Attachments());
        assertTrue(po.Audit() instanceof PurchaseOrderAudit);
        assertSame(po.Audit(), po.Audit());
        assertTrue(po.Search() instanceof PurchaseOrderSearch);
        assertSame(po.Search(), po.Search());
        assertTrue(po.Suggestions() instanceof PurchaseOrderSuggestions);
        assertSame(po.Suggestions(), po.Suggestions());
    }

    // =====================================================================
    // Searches (delegated to PurchaseOrderSearch)
    // =====================================================================
    @Test
    public void searchesAreDelegated() throws Exception {
        PurchaseOrderSearch s = stub.search;
        assertEquals("success", result(po.SearchBranch("x", true)));
        verify(s).branch("x", true);
        po.SearchIndustry("x", false);
        verify(s).industry("x", false);
        po.SearchCategory("x", false);
        verify(s).category("x", false);
        po.SearchTerm("x", false);
        verify(s).term("x", false);
        po.SearchBarcode("x", true, 0, true);
        verify(s).inventory("x", true, 0, true, true, RecordStatus.ACTIVE, "Model: ", false);
        po.SearchBarcodeDescription("x", false, 0, false);
        verify(s).inventory("x", false, 0, false, true, RecordStatus.ACTIVE, "Barcode: ", false);
        po.SearchModel("x", false, 1, true);
        verify(s).inventory("x", false, 1, true, true, "123", "Barcode: ", true);
        po.SearchSupplier("x", false);
        verify(s).supplier("x", false);
        po.SearchCompany("x", false);
        verify(s).company("x", false);
        po.SearchDestination("x", false);
        verify(s).destination("x", false);
        po.SearchDepartment("x", false);
        verify(s).department("x", false);
        po.SearchBrand("x", false, 0);
        verify(s).brand("x", false, 0);
        po.SelectSupplier("C001");
        verify(s).pickSupplier("C001");
        po.SelectTerm("T1");
        verify(s).pickTerm("T1");
        po.SelectDestination("M001");
        verify(s).pickDestination("M001");
        po.SelectBarcode("S1", 0);
        verify(s).pickInventory("S1", 0, "Model: ", false);
        po.SelectBarcodeDescription("S1", 0);
        verify(s).pickInventory("S1", 0, "Barcode: ", false);
        assertSame(stub.suggestions, po.Suggestions());
        assertEquals(Collections.emptyList(), po.Suggestions().supplier("a"));
    }
}
