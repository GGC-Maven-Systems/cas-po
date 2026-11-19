package ph.com.guanzongroup.cas.po.validator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.simple.JSONObject;
import org.junit.Test;
import ph.com.guanzongroup.cas.po.status.PurchaseOrderStatus;

/**
 * Status changes allowed by PurchaseOrderValidator.validateStatusChange (the
 * rules of cas-purchasing's StatusChangeValidator.validatePOStatChange).
 */
public class PurchaseOrderStatusChangeTest {

    private static void ok(String fsCurrent, String fsNew) {
        JSONObject loJSON = PurchaseOrderValidator.validateStatusChange(fsCurrent, fsNew);
        assertEquals(fsCurrent + " -> " + fsNew + ": " + loJSON, "success", loJSON.get("result"));
    }

    private static void refused(String fsCurrent, String fsNew, String fsMessagePart) {
        JSONObject loJSON = PurchaseOrderValidator.validateStatusChange(fsCurrent, fsNew);
        assertEquals(fsCurrent + " -> " + fsNew, "error", loJSON.get("result"));
        assertTrue(String.valueOf(loJSON.get("message")), String.valueOf(loJSON.get("message")).contains(fsMessagePart));
    }

    @Test
    public void confirm_fromOpenOrReturnedOnly() {
        ok(PurchaseOrderStatus.OPEN, PurchaseOrderStatus.CONFIRMED);
        ok(PurchaseOrderStatus.RETURNED, PurchaseOrderStatus.CONFIRMED);
        refused(PurchaseOrderStatus.CONFIRMED, PurchaseOrderStatus.CONFIRMED, "already confirmed");
        refused(PurchaseOrderStatus.APPROVED, PurchaseOrderStatus.CONFIRMED, "confirmation failed");
    }

    @Test
    public void cancel_fromConfirmedOrReturnedOnly() {
        ok(PurchaseOrderStatus.CONFIRMED, PurchaseOrderStatus.CANCELLED);
        ok(PurchaseOrderStatus.RETURNED, PurchaseOrderStatus.CANCELLED);
        refused(PurchaseOrderStatus.OPEN, PurchaseOrderStatus.CANCELLED, "CANCELLATION failed");
        refused(PurchaseOrderStatus.CANCELLED, PurchaseOrderStatus.CANCELLED, "already CANCELLED");
    }

    @Test
    public void void_fromOpenOrReturnedOnly() {
        ok(PurchaseOrderStatus.OPEN, PurchaseOrderStatus.VOID);
        refused(PurchaseOrderStatus.CONFIRMED, PurchaseOrderStatus.VOID, "VOID failed");
    }

    @Test
    public void post_fromConfirmedOnly() {
        ok(PurchaseOrderStatus.CONFIRMED, PurchaseOrderStatus.POSTED);
        refused(PurchaseOrderStatus.APPROVED, PurchaseOrderStatus.POSTED, "posting failed");
    }

    @Test
    public void pendingLetterCountsAsItsStatus() {
        //'A' = CONFIRMED pending approval
        refused("A", PurchaseOrderStatus.CONFIRMED, "already confirmed");
        ok("A", PurchaseOrderStatus.CANCELLED);
    }

    @Test
    public void otherStatusesAreNotRestrictedHere() {
        ok(PurchaseOrderStatus.CONFIRMED, PurchaseOrderStatus.APPROVED);
        ok(PurchaseOrderStatus.CONFIRMED, PurchaseOrderStatus.RETURNED);
    }
}
