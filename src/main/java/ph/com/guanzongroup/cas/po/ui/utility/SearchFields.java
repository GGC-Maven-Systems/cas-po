package ph.com.guanzongroup.cas.po.ui.utility;

import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;

/**
 * Marks searchable text fields: a magnifier at the right of the field
 * (style class {@code search-field} in StyleSheet.css) and a tooltip, in
 * place of the "Press F3: Search" prompt text.
 */
public final class SearchFields {

    public static final String STYLE_CLASS = "search-field";
    public static final String TOOLTIP = "Enter or F3: search";

    private SearchFields() {
    }

    public static void mark(TextField... faFields) {
        for (TextField loField : faFields) {
            if (!loField.getStyleClass().contains(STYLE_CLASS)) {
                loField.getStyleClass().add(STYLE_CLASS);
            }
            if (loField.getTooltip() == null) {
                loField.setTooltip(new Tooltip(TOOLTIP));
            }
        }
    }
}
