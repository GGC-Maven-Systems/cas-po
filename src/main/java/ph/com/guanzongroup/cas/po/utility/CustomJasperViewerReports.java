package ph.com.guanzongroup.cas.po.utility;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.Platform;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperPrintManager;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleHtmlExporterOutput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import net.sf.jasperreports.swing.JRViewer;
import net.sf.jasperreports.swing.JRViewerToolbar;
import net.sf.jasperreports.view.JasperViewer;
import org.guanzon.appdriver.agent.ShowMessageFX;

/**
 * Report preview window: JasperViewer with its own Save (PDF, Excel, Word,
 * CSV, HTML, JasperReports XML) and Print buttons, and one window per report
 * title (showing a title that is already open brings it to the front).
 *
 * <p>
 * Copied from ph.com.guanzongroup.cas.po.utility.CustomJasperViewerReports.
 * Changes: saving as JasperReports XML works (it cast the print to a report
 * design and always failed); the open-window list is thread-safe; an unused
 * cas-cashflow import is gone.</p>
 */
public class CustomJasperViewerReports extends JasperViewer {

    private static final Map<String, CustomJasperViewerReports> OPEN_VIEWERS = new ConcurrentHashMap<>();

    private final String reportTitle;

    /** Shows the report, or brings its window to the front when it is already open. */
    public static void show(final JasperPrint jasperPrint, final String reportTitle) {
        CustomJasperViewerReports viewer = OPEN_VIEWERS.get(reportTitle);
        if (viewer != null && viewer.isDisplayable()) {
            viewer.setState(JFrame.NORMAL);
            viewer.toFront();
            viewer.requestFocus();
            return;
        }

        viewer = new CustomJasperViewerReports(jasperPrint, reportTitle);
        OPEN_VIEWERS.put(reportTitle, viewer);
        viewer.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                OPEN_VIEWERS.remove(reportTitle);
            }
        });
        viewer.setVisible(true);
    }

    public CustomJasperViewerReports(final JasperPrint jasperPrint, final String reportTitle) {
        super(jasperPrint, false);
        this.reportTitle = reportTitle;
        customizeToolbar(jasperPrint);
    }

    // =====================================================================
    // Toolbar: own Save and Print
    // =====================================================================
    private void customizeToolbar(final JasperPrint jasperPrint) {
        JRViewer viewer = findViewer(getContentPane());
        if (viewer == null) {
            return;
        }
        for (Component component : viewer.getComponents()) {
            if (component instanceof JRViewerToolbar) {
                JRViewerToolbar toolbar = (JRViewerToolbar) component;
                for (Component button : toolbar.getComponents()) {
                    if (button instanceof JButton) {
                        replaceAction((JButton) button, jasperPrint);
                    }
                }
                toolbar.revalidate();
                toolbar.repaint();
            }
        }
    }

    private void replaceAction(JButton button, final JasperPrint jasperPrint) {
        String tooltip = button.getToolTipText();
        if ("Save".equals(tooltip)) {
            button.setEnabled(true);
            button.setVisible(true);
            removeActions(button);
            button.addActionListener(e -> saveReport(jasperPrint));
        } else if ("Print".equals(tooltip)) {
            removeActions(button);
            button.addActionListener(e -> printReport(jasperPrint));
        }
    }

    private static void removeActions(JButton button) {
        for (ActionListener listener : button.getActionListeners()) {
            button.removeActionListener(listener);
        }
    }

    private static JRViewer findViewer(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JRViewer) {
                return (JRViewer) component;
            }
            if (component instanceof Container) {
                JRViewer viewer = findViewer((Container) component);
                if (viewer != null) {
                    return viewer;
                }
            }
        }
        return null;
    }

    // =====================================================================
    // Print
    // =====================================================================
    private void printReport(final JasperPrint jasperPrint) {
        try {
            if (JasperPrintManager.printReport(jasperPrint, true)) {
                inform("Report printed successfully.");
            } else {
                warn("Printing was canceled by the user.");
                toFront();
            }
        } catch (JRException ex) {
            warn("Print failed: " + ex.getMessage());
            toFront();
        }
    }

    // =====================================================================
    // Save
    // =====================================================================
    private void saveReport(final JasperPrint jasperPrint) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Report");
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("PDF (*.pdf)", "pdf"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Excel Workbook (*.xlsx)", "xlsx"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("Word Document (*.docx)", "docx"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("CSV (*.csv)", "csv"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("HTML (*.html)", "html"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("JR XML (*.jrpxml)", "jrpxml"));
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            warn("Saving was canceled by the user.");
            return;
        }

        try {
            String extension = ((FileNameExtensionFilter) chooser.getFileFilter()).getExtensions()[0];
            File file = chooser.getSelectedFile();
            String fileName = file.getAbsolutePath();
            if (!fileName.toLowerCase().endsWith("." + extension)) {
                fileName += "." + extension;
            }

            export(jasperPrint, extension.toLowerCase(), fileName);
            inform("Report saved successfully.\n\n" + fileName);
        } catch (Exception ex) {
            warn("Save failed:\n" + ex.getMessage());
            SwingUtilities.invokeLater(() -> {
                setVisible(true);
                toFront();
                requestFocus();
            });
        }
    }

    private static void export(JasperPrint jasperPrint, String extension, String fileName) throws JRException {
        switch (extension) {
            case "pdf":
                JasperExportManager.exportReportToPdfFile(jasperPrint, fileName);
                break;
            case "xlsx": {
                JRXlsxExporter exporter = new JRXlsxExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(fileName));
                SimpleXlsxReportConfiguration config = new SimpleXlsxReportConfiguration();
                config.setOnePagePerSheet(false);
                config.setDetectCellType(true);
                config.setCollapseRowSpan(false);
                exporter.setConfiguration(config);
                exporter.exportReport();
                break;
            }
            case "docx": {
                JRDocxExporter exporter = new JRDocxExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(fileName));
                exporter.exportReport();
                break;
            }
            case "csv": {
                JRCsvExporter exporter = new JRCsvExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleWriterExporterOutput(fileName));
                exporter.exportReport();
                break;
            }
            case "html": {
                HtmlExporter exporter = new HtmlExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleHtmlExporterOutput(fileName));
                exporter.exportReport();
                break;
            }
            case "jrpxml":
                //the filled report as JasperReports XML (was a cast to JRReport that always failed)
                JasperExportManager.exportReportToXmlFile(jasperPrint, fileName, false);
                break;
            default:
                throw new IllegalArgumentException("Unsupported file format: " + extension);
        }
    }

    // =====================================================================
    // Messages (JavaFX dialogs from the Swing window)
    // =====================================================================
    private void inform(String fsMessage) {
        Platform.runLater(() -> ShowMessageFX.Information(fsMessage, reportTitle, null));
    }

    private void warn(String fsMessage) {
        Platform.runLater(() -> ShowMessageFX.Warning(fsMessage, reportTitle, null));
    }
}
