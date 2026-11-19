package ph.com.guanzongroup.cas.po.utility;

/**
 * One row of an auto-suggest list: the record's key, the code and name the
 * user sees, an optional unit of measure (items) and an optional detail
 * (address, brand / model, ...).
 */
public final class Suggestion {

    private final String id;
    private final String code;
    private final String name;
    private final String unit;
    private final String detail;

    public Suggestion(String fsId, String fsCode, String fsName, String fsDetail) {
        this(fsId, fsCode, fsName, null, fsDetail);
    }

    public Suggestion(String fsId, String fsCode, String fsName, String fsUnit, String fsDetail) {
        id = text(fsId);
        code = text(fsCode);
        name = text(fsName);
        unit = text(fsUnit);
        detail = text(fsDetail);
    }

    /** Key used to load the record (client id, branch code, stock id, ...). */
    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    /** Unit of measure (items); empty for other lists. */
    public String getUnit() {
        return unit;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        String lsText = code + "  " + name;
        if (!unit.isEmpty()) {
            lsText += "  " + unit;
        }
        return detail.isEmpty() ? lsText : lsText + "  (" + detail + ")";
    }

    private static String text(String fsValue) {
        return fsValue == null ? "" : fsValue.trim();
    }
}
