package ph.com.guanzongroup.cas.po.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.sql.ResultSet;
import org.guanzon.appdriver.base.GRiderCAS;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import ph.com.guanzongroup.cas.po.model.Model_PO_Master;

/** getPurchaseOrderList: the Confirmation table in one query, without opening each PO. */
public class PurchaseOrderListTest {

    private static class TestPO extends PurchaseOrderGeneral {

        TestPO(GRiderCAS foGRider, Model_PO_Master foMaster, String fsStatus) {
            poGRider = foGRider;
            poMaster = foMaster;
            psTranStat = fsStatus;
        }
    }

    @Test
    public void rowsAndFilters() throws Exception {
        GRiderCAS loGRider = mock(GRiderCAS.class);
        when(loGRider.isMainOffice()).thenReturn(true);
        ResultSet loRS = mock(ResultSet.class);
        when(loRS.next()).thenReturn(true, false);
        when(loRS.getString("sTransNox")).thenReturn("M00126000001");
        when(loRS.getDate("dTransact")).thenReturn(Date.valueOf("2026-11-19"));
        when(loRS.getString("sCompnyNm")).thenReturn("ACME TRADING");
        when(loRS.getString("sBranchNm")).thenReturn("MAIN");
        when(loRS.getString("cTranStat")).thenReturn("0");
        when(loGRider.executeQuery(anyString())).thenReturn(loRS);

        Model_PO_Master loMaster = mock(Model_PO_Master.class);
        when(loMaster.getIndustryID()).thenReturn("05");
        when(loMaster.getCompanyID()).thenReturn("0001");
        when(loMaster.getCategoryCode()).thenReturn("0002");

        JSONObject loJSON = new TestPO(loGRider, loMaster, "0").getPurchaseOrderList("C001", "");

        assertEquals("success", loJSON.get("result"));
        JSONArray laRows = (JSONArray) loJSON.get("data");
        assertEquals(1, laRows.size());
        JSONObject loRow = (JSONObject) laRows.get(0);
        assertEquals("M00126000001", loRow.get("sTransNox"));
        assertEquals("ACME TRADING", loRow.get("sCompnyNm"));
        assertEquals(Date.valueOf("2026-11-19"), loRow.get("dTransact"));

        ArgumentCaptor<String> loSQL = ArgumentCaptor.forClass(String.class);
        Mockito.verify(loGRider).executeQuery(loSQL.capture());
        String lsSQL = loSQL.getValue();
        assertTrue(lsSQL, lsSQL.contains("a.sIndstCdx = '05'"));
        assertTrue(lsSQL, lsSQL.contains("a.sSupplier LIKE '%C001'"));
        assertTrue(lsSQL, lsSQL.contains("a.cTranStat"));
        assertTrue(lsSQL, lsSQL.endsWith("ORDER BY a.dTransact, e.sCompnyNm, a.sTransNox ASC"));
    }
}
