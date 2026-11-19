package ph.com.guanzongroup.cas.po.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.guanzon.appdriver.agent.services.Model;
import org.guanzon.appdriver.base.GRiderCAS;
import org.guanzon.cas.client.account.AP_Client_Master;
import org.guanzon.cas.client.services.ClientControllers;
import org.guanzon.cas.inv.Inventory;
import org.guanzon.cas.inv.services.InvControllers;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import ph.com.guanzongroup.cas.po.model.Model_PO_Detail;
import ph.com.guanzongroup.cas.po.model.Model_PO_Master;
import ph.com.guanzongroup.cas.po.utility.Suggestion;
import ph.com.guanzongroup.cas.po.validator.PurchaseOrderValidator;
import ph.com.guanzongroup.cas.po.validator.PurchaseOrder_General;

/** The auto-suggest queries: same filters as the lookups, top 15, no dialog. */
public class PurchaseOrderSuggestionsTest {

    private static class TestPO extends PurchaseOrder {

        @Override
        protected PurchaseOrderValidator createValidator() {
            return new PurchaseOrder_General();
        }

        @Override
        public void initSQL() {
        }

        @Override
        public JSONObject willSave() {
            return successJSON();
        }

        @Override
        public JSONObject saveOthers() {
            return successJSON();
        }
    }

    private static final String INVENTORY_SQL = "SELECT a.sStockIDx, a.sBarCodex, a.sDescript FROM Inventory a"
            + " LEFT JOIN Inv_Supplier g ON a.sStockIDx = g.sStockIDx WHERE a.cRecdStat = '1'";
    private static final String SUPPLIER_SQL = "SELECT a.sClientID, b.sCompnyNm FROM AP_Client_Master a"
            + " LEFT JOIN Client_Master b ON a.sClientID = b.sClientID WHERE a.cRecdStat = '1'";

    private TestPO po;
    private Model_PO_Master master;
    private Model_PO_Detail row;
    private GRiderCAS grider;
    private PurchaseOrderSuggestions suggest;

    @Before
    public void setUp() throws Exception {
        po = new TestPO();
        master = mock(Model_PO_Master.class);
        po.poMaster = master;
        row = mock(Model_PO_Detail.class);
        po.paDetail = new ArrayList<Model>();
        po.paDetail.add(row);

        grider = mock(GRiderCAS.class);
        ResultSet loRS = mock(ResultSet.class);
        when(loRS.next()).thenReturn(true, true, false);
        when(loRS.getString("sStockIDx")).thenReturn("S1", "S2");
        when(loRS.getString("sBarCodex")).thenReturn("4800001", "4800002");
        when(loRS.getString("sDescript")).thenReturn("BOND PAPER A4", "BOND PAPER LONG");
        when(loRS.getString("xBrandNme")).thenReturn("HARD COPY", "");
        when(loRS.getString("xModelNme")).thenReturn("", "");
        when(loRS.getString("xMeasurNm")).thenReturn("REAM", "REAM");
        when(loRS.getString("sClientID")).thenReturn("C001");
        when(loRS.getString("sCompnyNm")).thenReturn("ACME TRADING");
        when(loRS.getString("xAddressx")).thenReturn("CALASIAO");
        when(grider.executeQuery(anyString())).thenReturn(loRS);

        Inventory loInventory = mock(Inventory.class);
        when(loInventory.getSQ_Browse()).thenReturn(INVENTORY_SQL);
        InvControllers loInvControllers = mock(InvControllers.class);
        when(loInvControllers.Inventory()).thenReturn(loInventory);

        AP_Client_Master loSupplier = mock(AP_Client_Master.class);
        when(loSupplier.getSQ_Browse()).thenReturn(SUPPLIER_SQL);
        ClientControllers loClients = mock(ClientControllers.class);
        when(loClients.APClientMaster()).thenReturn(loSupplier);

        suggest = new PurchaseOrderSuggestions(po, () -> grider, () -> null, () -> loInvControllers, () -> loClients);
    }

    private String lastSQL() throws Exception {
        ArgumentCaptor<String> loSQL = ArgumentCaptor.forClass(String.class);
        verify(grider).executeQuery(loSQL.capture());
        return loSQL.getValue();
    }

    @Test
    public void barcodeUsesTheLookupFilters() throws Exception {
        when(master.getSupplierID()).thenReturn("C001");
        when(master.getCategoryCode()).thenReturn("0002");
        when(master.getIndustryID()).thenReturn("05");
        when(row.getBrandId()).thenReturn("B01");

        List<Suggestion> laRows = suggest.barcode("4800", 0, true, false);

        String lsSQL = lastSQL();
        assertTrue(lsSQL, lsSQL.contains("g.sSupplier = 'C001'"));
        assertTrue(lsSQL, lsSQL.contains("a.sBrandIDx = 'B01'"));
        assertTrue(lsSQL, lsSQL.contains("a.sCategCd1 = '0002'"));
        assertFalse(lsSQL, lsSQL.contains("a.sIndstCdx")); //General: any industry
        assertTrue(lsSQL, lsSQL.contains("a.sBarCodex LIKE '4800%'"));
        assertTrue(lsSQL, lsSQL.endsWith(" GROUP BY a.sStockIDx ORDER BY a.sBarCodex LIMIT 15"));

        assertEquals(2, laRows.size());
        assertEquals("S1", laRows.get(0).getId());
        assertEquals("4800001", laRows.get(0).getCode());
        assertEquals("BOND PAPER A4", laRows.get(0).getName());
        assertEquals("REAM", laRows.get(0).getUnit());
        assertEquals("HARD COPY", laRows.get(0).getDetail());
        assertEquals("REAM", laRows.get(1).getUnit());
        assertEquals("", laRows.get(1).getDetail());
    }

    @Test
    public void descriptionMatchesAnywhereAndCanFilterIndustryAndAllSuppliers() throws Exception {
        when(master.getSupplierID()).thenReturn("C001");
        when(master.getIndustryID()).thenReturn("05");

        suggest.description("paper", 0, false, true);

        String lsSQL = lastSQL();
        assertFalse(lsSQL, lsSQL.contains("g.sSupplier"));
        assertTrue(lsSQL, lsSQL.contains("a.sIndstCdx = '05'"));
        assertTrue(lsSQL, lsSQL.contains("a.sDescript LIKE '%paper%'"));
    }

    @Test
    public void noQueryForBlankTextOrMissingRow() throws Exception {
        assertTrue(suggest.barcode("  ", 0, true, false).isEmpty());
        assertTrue(suggest.barcode("48", -1, true, false).isEmpty());
        assertTrue(suggest.barcode("48", 5, true, false).isEmpty());
        assertTrue(suggest.supplier("").isEmpty());
        verify(grider, never()).executeQuery(anyString());
    }

    @Test
    public void supplierByIdOrName() throws Exception {
        List<Suggestion> laRows = suggest.supplier("acme");

        String lsSQL = lastSQL();
        assertTrue(lsSQL, lsSQL.contains("(a.sClientID LIKE 'acme%' OR b.sCompnyNm LIKE '%acme%')"));
        assertTrue(lsSQL, lsSQL.endsWith(" ORDER BY b.sCompnyNm LIMIT 15"));
        assertEquals("C001", laRows.get(0).getId());
        assertEquals("ACME TRADING", laRows.get(0).getName());
        assertEquals("CALASIAO", laRows.get(0).getDetail());
    }

    @Test
    public void wildcardsTypedByTheUserMatchThemselves() {
        assertEquals("'50\\\\%%'", PurchaseOrderSuggestions.startsWith("50%"));
        assertEquals("'%a\\\\_b%'", PurchaseOrderSuggestions.contains(" a_b "));
        assertEquals("'%O''NEIL%'", PurchaseOrderSuggestions.contains("O'NEIL"));
        assertEquals("", PurchaseOrderSuggestions.join(null, " ", ""));
        assertEquals("A / B", PurchaseOrderSuggestions.join("A", "", "B"));
    }

    @Test
    public void suggestionText() {
        assertEquals("C001  ACME", new Suggestion("C001", "C001", "ACME", null).toString());
        assertEquals("4800  PAPER  (HARD COPY)", new Suggestion("S1", "4800", "PAPER", " HARD COPY ").toString());
        assertEquals("4800  PAPER  REAM  (HARD COPY)", new Suggestion("S1", "4800", "PAPER", "REAM", "HARD COPY").toString());
        assertEquals("", new Suggestion("C001", "C001", "ACME", null).getUnit());
    }
}
