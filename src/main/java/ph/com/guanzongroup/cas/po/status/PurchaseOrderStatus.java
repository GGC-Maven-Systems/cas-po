package ph.com.guanzongroup.cas.po.status;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Purchase Order codes stored in the database: transaction status
 * (cTranStat), source document codes (sSourceCd), processed flag (cProcessd)
 * and industry codes.
 *
 * <p>
 * Same values as ph.com.guanzongroup.cas.po.status.PurchaseOrderStatus /
 * PurchaseOrderProcessedStatus. For labels and pending (letter) codes see
 * {@link POStatus}.</p>
 */
public final class PurchaseOrderStatus {

    public static final String OPEN = "0";
    public static final String CONFIRMED = "1";
    public static final String PROCESSED = "2";
    public static final String CANCELLED = "3";
    public static final String VOID = "4";
    public static final String APPROVED = "5";
    public static final String POSTED = "6";
    public static final String RETURNED = "9";

    private PurchaseOrderStatus() {
    }

    /** Inventory ledger direction. */
    public static final class Reverse {

        public static final String INCLUDE = "+";
        public static final String EXCLUDE = "-";

        private Reverse() {
        }
    }

    /** Source document of a PO line (PO_Detail.sSourceCd). */
    public static final class SourceCode {

        public static final String STOCKREQUEST = "InvR";
        public static final String POQUOTATION = "POQt";

        private SourceCode() {
        }
    }

    /** PO_Master.cProcessd. */
    public static final class Processed {

        public static final String NO = "0";
        public static final String YES = "1";

        private Processed() {
        }
    }

    /** Industry codes (sIndstCdx). */
    public static final class IndustryCode {

        public static final String MOTORCYCLE = "01";
        public static final String MOBILEPHONE = "02";
        public static final String AUTO_GROUP_HONDA = "03";
        public static final String MONARCH = "04";
        public static final String AUTO_GROUP_NISSAN = "05";
        public static final String AUTO_GROUP_ANY = "06";
        public static final String GUANZON_SERVICE_OFFICE = "07";
        public static final String MAIN_OFFICE = "08";
        public static final String GENERAL = "09";
        public static final String ENGINEERING = "10";
        public static final String APPLIANCES = "11";
        public static final String PEDRITOS = "12";

        private IndustryCode() {
        }
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

}
