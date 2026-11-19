package ph.com.guanzongroup.cas.po.status;

/**
 * Purchase Order constants: default values and print layouts (same values as
 * ph.com.guanzongroup.cas.po.status.PurchaseOrderStaticData).
 */
public final class PurchaseOrderStaticData {

    public static final String PurchaseOrder = "007";
    public static final String default_value_string = "0";
    public static final String default_empty_string = "";
    public static final double default_value_double = 0.00;

    /** Print layout (printTransaction). */
    public static final String Printing_CAR_MC_MPUnit_Appliance = "0000001";
    public static final String Printing_CARSp_MCSp_General = "0000002";
    public static final String Printing_Pedritos = "0000003";

    private PurchaseOrderStaticData() {
    }
}
