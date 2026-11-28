package ph.com.guanzongroup.cas.po.status;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Purchase Order constants: default values and print layouts (same values as
 * ph.com.guanzongroup.cas.po.status.PurchaseOrderStaticData).
 */
public final class PurchaseOrderStaticData {

    public static final String PurchaseOrder = "007";
    public static final String default_value_string = "0";
    public static final String default_empty_string = "";
    public static final double default_value_double = 0.00;

    /**
     * Default term: none. A new PO starts without a term, and clearing or
     * changing the supplier clears it; the user picks one (required on save).
     */
    public static final String DEFAULT_TERM = "";

    /** Print layout (printTransaction). */
    public static final String Printing_CAR_MC_MPUnit_Appliance = "0000001";
    public static final String Printing_CARSp_MCSp_General = "0000002";
    public static final String Printing_Pedritos = "0000003";

    /** Form names used as message-box titles. */
    public static final String FORM_NAME_PURCHASE_ORDER = "Purchase Order";
    public static final String FORM_NAME_RECIPIENTS = "Purchase Order Recipients";
    public static final String FORM_NAME_NEW_SUPPLIER_EMAIL = "New Supplier Email";

    /** Screens (src/main/resources). */
    public static final String RECIPIENT_FXML = "/ph/com/guanzongroup/cas/po/ui/PurchaseOrderRecipient.fxml";
    public static final String RECIPIENT_EMAIL_FXML = "/ph/com/guanzongroup/cas/po/ui/PurchaseOrderRecipientEmail.fxml";

    /** Recipient type labels (display only; the stored codes are PurchaseOrderMP.RECIPIENT_*). */
    public static final String RECIPIENT_TYPE_TO = "To";
    public static final String RECIPIENT_TYPE_CC = "CC";
    public static final String RECIPIENT_TYPE_BCC = "BCC";
    public static final ObservableList<String> RECIPIENT_TYPES =
            FXCollections.observableArrayList(RECIPIENT_TYPE_TO, RECIPIENT_TYPE_CC, RECIPIENT_TYPE_BCC);

    /**
     * Inventory type code of a detail row whose barcode and description can be
     * typed in (CHECK: confirm the name of this inventory type).
     */
    public static final String INVENTORY_TYPE_FREE_ENTRY = "0007";

    private PurchaseOrderStaticData() {
    }

    /**
     * Order type descriptions used in JavaFX ComboBox controls.
     */
    public static final ObservableList<String> ORDER_TYPE_DESCRIPTION =
            FXCollections.observableArrayList(
                    "Regular",
                    "Indent",
                    "Urgent",
                    "Special Order",
                    "Bulk Order"
            );

    /**
     * Order type codes corresponding to {}.
     * Same order as {@link #ORDER_TYPE_DESCRIPTION}.
     */
    public static final String[] ORDER_TYPE_CODE = {
            "0",//Regular
            "1",//Indent
            "2",//Urgent
            "3",//Special Order
            "4"//Bulk Order
    };

    // ---------------------------------------------------------------------
// Add these to ph.com.guanzongroup.cas.po.status.PurchaseOrderStaticData
// (needs: import javafx.collections.FXCollections;
//         import javafx.collections.ObservableList;)
// ---------------------------------------------------------------------

    // ===== Landing screen filters =====

    /** First item of every filter combo: no filter. */
    public static final String FILTER_ALL = "All";

    /** Date From = today minus this many months (Date To = today). */
    public static final int FILTER_DEFAULT_DATE_RANGE_MONTHS = 1;

    public static final int FILTER_DEFAULT_PAGE_SIZE = 50;
    public static final ObservableList<Integer> FILTER_PAGE_SIZES =
            FXCollections.observableArrayList(25, 50, 100, 200);

    /** Status filter: description shown, and the status letters sent to the query (same index). */
    public static final ObservableList<String> STATUS_FILTER_DESCRIPTION = FXCollections.observableArrayList(
            FILTER_ALL, "Open", "Confirmed", "Approved", "Returned", "Posted", "Cancelled", "Void");

    public static final String[] STATUS_FILTER_CODE = {
            PurchaseOrderStatus.OPEN + PurchaseOrderStatus.CONFIRMED + PurchaseOrderStatus.APPROVED
                    + PurchaseOrderStatus.RETURNED + PurchaseOrderStatus.POSTED
                    + PurchaseOrderStatus.CANCELLED + PurchaseOrderStatus.VOID, // All
            PurchaseOrderStatus.OPEN,
            PurchaseOrderStatus.CONFIRMED,
            PurchaseOrderStatus.APPROVED,
            PurchaseOrderStatus.RETURNED,
            PurchaseOrderStatus.POSTED,
            PurchaseOrderStatus.CANCELLED,
            PurchaseOrderStatus.VOID
    };

    // ===== Landing screen summary cards =====
    // Each status letter is in one card, so Pending + Approved + Voided/Returned = Total.

    /** PENDING card: waiting to be confirmed or approved. */
    public static final String[] SUMMARY_PENDING_STATUS = {
            PurchaseOrderStatus.OPEN, PurchaseOrderStatus.CONFIRMED
    };

    /** APPROVED card: approved, and approved POs already in use. */
    public static final String[] SUMMARY_APPROVED_STATUS = {
            PurchaseOrderStatus.APPROVED, PurchaseOrderStatus.POSTED
    };

    /** VOIDED / RETURNED card. */
    public static final String[] SUMMARY_VOIDED_STATUS = {
            PurchaseOrderStatus.RETURNED, PurchaseOrderStatus.CANCELLED, PurchaseOrderStatus.VOID
    };


    // ===== Landing screen: double click opens the preview =====
    // Use the same style of path as RECIPIENT_FXML (relative to the controller's package, or absolute).
    public static final String PREVIEW_FXML = "/ph/com/guanzongroup/cas/po/ui/PurchaseOrderMP_Preview.fxml";
}