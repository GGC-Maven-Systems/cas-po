package ph.com.guanzongroup.cas.po.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import org.guanzon.appdriver.base.GRiderCAS;
import org.guanzon.appdriver.base.SQLUtil;
import org.guanzon.appdriver.constant.EditMode;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import ph.com.guanzongroup.cas.po.controller.HeadlessUserPrompt;
import ph.com.guanzongroup.cas.po.controller.PurchaseOrderGeneral;
import ph.com.guanzongroup.cas.po.services.PurchaseOrderControllers;
import ph.com.guanzongroup.cas.po.status.PurchaseOrderStatus;

/**
 * Integration test of the General Inventory Purchase Order against a TEST
 * database: create, save, reopen and edit, list and report, confirm (with the
 * automatic approval), then void or cancel it.
 *
 * <p>
 * It writes real records (the PO is left voided or cancelled, with the remark
 * "cas-po integration test"). Run it only against a test database:</p>
 *
 * <pre>
 * mvn verify -Dpo.it.config=D:/GGC_Test_Systems
 * </pre>
 *
 * <p>
 * Optional: -Dpo.it.industry, -Dpo.it.company, -Dpo.it.category (default:
 * user.selected.* of cas.properties), -Dpo.it.supplier and -Dpo.it.stock (a
 * supplier and one of its items; default: the first active pair found),
 * -Dpo.it.term (default: the first active term).</p>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PurchaseOrderGeneralIT {

    private static final String REMARKS = "cas-po integration test";

    private static GRiderCAS app;
    private static String industry;
    private static String company;
    private static String category;
    private static String supplier;
    private static String stockId;
    private static String term;
    /** The PO created by the first test. */
    private static String transNo;

    @BeforeClass
    public static void connect() throws SQLException {
        app = ItDatabase.connect();
        industry = ItDatabase.setting("po.it.industry", System.getProperty("user.selected.industry", ""));
        company = ItDatabase.setting("po.it.company", System.getProperty("user.selected.company", ""));
        category = ItDatabase.setting("po.it.category", System.getProperty("user.selected.category", ""));

        supplier = ItDatabase.setting("po.it.supplier", null);
        stockId = ItDatabase.setting("po.it.stock", null);
        if (supplier == null || stockId == null) {
            String lsSQL = "SELECT g.sSupplier, a.sStockIDx"
                    + " FROM Inventory a"
                    + " JOIN Inv_Supplier g ON g.sStockIDx = a.sStockIDx"
                    + " JOIN AP_Client_Master s ON s.sClientID = g.sSupplier AND s.cRecdStat = '1'"
                    + " WHERE a.cRecdStat = '1'"
                    + (category.isEmpty() ? "" : " AND a.sCategCd1 = " + SQLUtil.toSQL(category))
                    + " LIMIT 1";
            supplier = ItDatabase.scalar(app, lsSQL, "sSupplier");
            stockId = ItDatabase.scalar(app, lsSQL, "sStockIDx");
        }
        term = ItDatabase.setting("po.it.term", ItDatabase.scalar(app,
                "SELECT sTermCode FROM Term WHERE cRecdStat = '1' ORDER BY sTermCode LIMIT 1", "sTermCode"));

        Assume.assumeTrue("No active supplier with an item found; set -Dpo.it.supplier and -Dpo.it.stock.",
                supplier != null && stockId != null);
        Assume.assumeTrue("No active term found; set -Dpo.it.term.", term != null);
        System.out.println("PO integration test: industry=" + industry + ", company=" + company + ", category=" + category
                + ", supplier=" + supplier + ", stock=" + stockId + ", term=" + term);
    }

    /** A controller like the screens use (factory setup), without dialogs. */
    private static PurchaseOrderGeneral controller() {
        PurchaseOrderGeneral loPO = new PurchaseOrderControllers(app, null).PurchaseOrderGeneral();
        loPO.setUserPrompt(new HeadlessUserPrompt());
        loPO.setTransactionStatus(PurchaseOrderStatus.OPEN + PurchaseOrderStatus.CONFIRMED + PurchaseOrderStatus.RETURNED);
        assertSuccess("InitTransaction", loPO.InitTransaction());
        return loPO;
    }

    private static void assertSuccess(String fsStep, JSONObject foResult) {
        assertNotNull(fsStep + " returned nothing", foResult);
        assertEquals(fsStep + ": " + foResult.get("message"), "success", foResult.get("result"));
    }

    /** Skips the test when the connected user needs an approving officer (no screen here). */
    private static void assumeNoOfficerNeeded(JSONObject foResult) {
        String lsMessage = String.valueOf(foResult.get("message"));
        Assume.assumeFalse("The connected user needs an approving officer: " + lsMessage,
                "error".equals(foResult.get("result")) && lsMessage.contains("approving officer"));
    }

    private static void setContext(PurchaseOrderGeneral foPO) throws SQLException {
        foPO.Master().setIndustryID(industry);
        foPO.Master().setCompanyID(company);
        foPO.Master().setCategoryCode(category);
        foPO.Master().setInventoryTypeCode(foPO.getInventoryTypeCode());
    }

    /** Total of the lines, then the net total (what the screen does). */
    private static void computeTotals(PurchaseOrderGeneral foPO) {
        double lnTotal = 0;
        for (int lnCtr = 0; lnCtr < foPO.getDetailCount(); lnCtr++) {
            lnTotal += foPO.Detail(lnCtr).getQuantity().doubleValue() * foPO.Detail(lnCtr).getUnitPrice().doubleValue();
        }
        foPO.Master().setTranTotal(lnTotal);
        foPO.computeNetTotal();
    }

    // =====================================================================
    @Test
    public void t1_newPurchaseOrderIsSaved() throws Exception {
        PurchaseOrderGeneral loPO = controller();
        assertSuccess("NewTransaction", loPO.NewTransaction());
        assertEquals(EditMode.ADDNEW, loPO.getEditMode());
        setContext(loPO);

        assertSuccess("SelectSupplier", loPO.SelectSupplier(supplier));
        assertSuccess("SelectTerm", loPO.SelectTerm(term));
        assertSuccess("SelectDestination", loPO.SelectDestination(app.getBranchCode()));
        loPO.Master().setRemarks(REMARKS);

        if (loPO.getDetailCount() == 0) {
            assertSuccess("AddDetail", loPO.AddDetail());
        }
        assertSuccess("SelectBarcode", loPO.SelectBarcode(stockId, 0));
        loPO.Detail(0).setQuantity(2);
        computeTotals(loPO);

        assertSuccess("SaveTransaction", loPO.SaveTransaction());
        transNo = loPO.Master().getTransactionNo();
        assertNotNull(transNo);
        assertFalse(transNo.isEmpty());
        System.out.println("PO integration test: created " + transNo);
    }

    @Test
    public void t2_savedPurchaseOrderIsReopenedAndEdited() throws Exception {
        Assume.assumeTrue("t1 did not create a PO", transNo != null);
        PurchaseOrderGeneral loPO = controller();
        assertSuccess("OpenTransaction", loPO.OpenTransaction(transNo));
        assertEquals(EditMode.READY, loPO.getEditMode());
        assertEquals(PurchaseOrderStatus.OPEN, loPO.Master().getTransactionStatus());
        assertEquals(supplier, loPO.Master().getSupplierID());
        assertEquals(REMARKS, loPO.Master().getRemarks());
        assertEquals(stockId, loPO.Detail(0).getStockID());
        assertEquals(2, loPO.Detail(0).getQuantity().intValue());

        assertSuccess("UpdateTransaction", loPO.UpdateTransaction());
        loPO.Detail(0).setQuantity(3);
        computeTotals(loPO);
        assertSuccess("SaveTransaction (edit)", loPO.SaveTransaction());

        PurchaseOrderGeneral loCheck = controller();
        assertSuccess("OpenTransaction (check)", loCheck.OpenTransaction(transNo));
        assertEquals(3, loCheck.Detail(0).getQuantity().intValue());
    }

    @Test
    public void t3_purchaseOrderWithoutSupplierIsRejected() throws Exception {
        PurchaseOrderGeneral loPO = controller();
        assertSuccess("NewTransaction", loPO.NewTransaction());
        setContext(loPO);
        assertSuccess("SelectTerm", loPO.SelectTerm(term));
        loPO.Master().setSupplierID("");

        JSONObject loResult = loPO.SaveTransaction();
        assertEquals("error", loResult.get("result"));
        assertEquals("Invalid Supplier.", loResult.get("message"));
    }

    @Test
    public void t4_purchaseOrderIsListedAndReported() throws Exception {
        Assume.assumeTrue("t1 did not create a PO", transNo != null);
        PurchaseOrderGeneral loPO = controller();
        setContext(loPO);

        JSONObject loList = loPO.getPurchaseOrderList(supplier, "");
        assertSuccess("getPurchaseOrderList", loList);
        assertTrue(transNo + " is not in the PO list", contains((JSONArray) loList.get("data"), transNo));

        LocalDate ldToday = LocalDate.now();
        JSONObject loReport = loPO.RetrieveSummaryReports(ldToday.minusDays(1), ldToday.plusDays(1), "", "", supplier, category);
        assertSuccess("RetrieveSummaryReports", loReport);
        assertTrue(transNo + " is not in the summary report", contains((JSONArray) loReport.get("data"), transNo));

        assertFalse("No supplier suggestions for " + supplier, loPO.Suggestions().supplier(supplier).isEmpty());
    }

    @Test
    public void t5_confirmingApprovesThePurchaseOrder() throws Exception {
        Assume.assumeTrue("t1 did not create a PO", transNo != null);
        PurchaseOrderGeneral loPO = controller();
        assertSuccess("OpenTransaction", loPO.OpenTransaction(transNo));

        JSONObject loResult = loPO.ConfirmTransaction("Integration test");
        assumeNoOfficerNeeded(loResult);
        assertTrue("ConfirmTransaction: " + loResult.get("message"),
                Arrays.asList("success", "matrix").contains(String.valueOf(loResult.get("result"))));

        PurchaseOrderGeneral loCheck = controller();
        assertSuccess("OpenTransaction (check)", loCheck.OpenTransaction(transNo));
        String lsStatus = loCheck.Master().getTransactionStatus();
        assertTrue("Unexpected status " + lsStatus, Arrays.asList(PurchaseOrderStatus.CONFIRMED, PurchaseOrderStatus.APPROVED,
                "A", "E").contains(lsStatus)); //A / E: confirm / approval pending in the authorization matrix
    }

    @Test
    public void t6_purchaseOrderIsVoidedOrCancelled() throws Exception {
        Assume.assumeTrue("t1 did not create a PO", transNo != null);
        PurchaseOrderGeneral loPO = controller();
        assertSuccess("OpenTransaction", loPO.OpenTransaction(transNo));

        boolean lbOpen = PurchaseOrderStatus.OPEN.equals(loPO.Master().getTransactionStatus());
        JSONObject loResult = lbOpen ? loPO.VoidTransaction("Integration test cleanup") : loPO.CancelTransaction("Integration test cleanup");
        assumeNoOfficerNeeded(loResult);
        assertTrue((lbOpen ? "VoidTransaction: " : "CancelTransaction: ") + loResult.get("message"),
                Arrays.asList("success", "matrix").contains(String.valueOf(loResult.get("result"))));

        if ("success".equals(loResult.get("result"))) {
            PurchaseOrderGeneral loCheck = controller();
            assertSuccess("OpenTransaction (check)", loCheck.OpenTransaction(transNo));
            assertEquals(lbOpen ? PurchaseOrderStatus.VOID : PurchaseOrderStatus.CANCELLED, loCheck.Master().getTransactionStatus());
        }
    }

    private static boolean contains(JSONArray faRows, String fsTransNo) {
        for (Object loRow : faRows) {
            if (fsTransNo.equals(((JSONObject) loRow).get("sTransNox"))) {
                return true;
            }
        }
        return false;
    }
}
