package ph.com.guanzongroup.cas.po.controller;

import static ph.com.guanzongroup.cas.po.utility.Results.error;
import static ph.com.guanzongroup.cas.po.utility.Results.success;
import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.guanzon.appdriver.base.GRiderCAS;
import org.json.simple.JSONObject;
import ph.com.guanzongroup.cas.po.model.Model_PO_Detail;
import ph.com.guanzongroup.cas.po.model.Model_PO_Master;
import ph.com.guanzongroup.cas.po.status.PurchaseOrderStaticData;
import ph.com.guanzongroup.cas.po.status.PurchaseOrderStatus;

/**
 * Exports the Purchase Order form to a PDF (named after the PO transaction
 * number) without opening the viewer. Used by every industry.
 *
 * <p>
 * The default layout is PurchaseOrderMPToEmail.jrxml. The report parameters
 * and fields filled here are exactly the ones that file declares:</p>
 * <ul>
 * <li>parameters: sBranchNm, sAddressx, sCompnyNm, sTransNox, sApprval1,
 * sApprval2, sRemarks, dTransDte, sCompany, sDestination,
 * sDestinationAddresss, sTerm, sSupplier, nQtyTotal;</li>
 * <li>fields (one row per item): nRowNo, nQty, sModelCode, sModelName,
 * sColor, nUnitPrice, nDisc, nTotal.</li>
 * </ul>
 *
 * <p>
 * Each PDF layout is a {@link Format} registered under a code: its JRXML,
 * how the item name is written and, optionally, extra report parameters. An
 * industry with its own layout calls {@code register(...)} (see
 * {@code createPdfExporter()} in the controller), then passes that code to
 * {@link #export(String)}.</p>
 */
public class PurchaseOrderPdfExporter {

    private static final Logger LOG = Logger.getLogger(PurchaseOrderPdfExporter.class.getName());

    /** Approver slots (sApprval1, sApprval2) the layout has. */
    private static final int MAX_APPROVERS = 2;

    /** Item name printed (sModelName) for one detail row. */
    @FunctionalInterface
    public interface DescriptionBuilder {

        String build(PurchaseOrder foPO, int fnRow) throws Exception;
    }

    /** Extra report parameters of a layout (added after the standard ones). */
    @FunctionalInterface
    public interface ExtraParameters {

        void add(PurchaseOrder foPO, Map<String, Object> foParams) throws Exception;
    }

    /** One PDF layout. */
    public static final class Format {

        final String jrxml;
        final DescriptionBuilder description;
        final ExtraParameters extra;

        Format(String fsJrxml, DescriptionBuilder foDescription, ExtraParameters foExtra) {
            jrxml = fsJrxml;
            description = foDescription;
            extra = foExtra;
        }
    }

    private final PurchaseOrder poPO;
    private final GRiderCAS poGRider;
    private final PurchaseOrderAudit poAudit;
    private final Map<String, Format> poFormats = new LinkedHashMap<>();

    public PurchaseOrderPdfExporter(PurchaseOrder foPO, GRiderCAS foGRider, PurchaseOrderAudit foAudit) {
        poPO = foPO;
        poGRider = foGRider;
        poAudit = foAudit;

        //the layouts the printer already has
        DescriptionBuilder loPlain = (loPO, lnRow) -> text(loPO.Detail(lnRow).Inventory().getDescription());
        DescriptionBuilder loBrandModel = (loPO, lnRow) -> {
            Model_PO_Detail loDetail = loPO.Detail(lnRow);
            return PurchaseOrderPrinter.join(
                    PurchaseOrderPrinter.join(text(loDetail.Inventory().Brand().getDescription()),
                            text(loDetail.Inventory().Model().getDescription())),
                    text(loDetail.Inventory().getDescription()));
        };
        register(PurchaseOrderStaticData.Printing_CAR_MC_MPUnit_Appliance,
                "PurchaseOrderMPToEmail.jrxml", loPlain);
        register(PurchaseOrderStaticData.Printing_CARSp_MCSp_General,
                "PurchaseOrderMPToEmail.jrxml", loBrandModel);
        register(PurchaseOrderStaticData.Printing_Pedritos,
                "PurchaseOrderMPToEmail.jrxml", loBrandModel);
    }

    // =====================================================================
    // Layouts
    // =====================================================================
    /** Adds (or replaces) a layout; jrxml is a file name in the config reports folder. */
    public PurchaseOrderPdfExporter register(String fsCode, String fsJrxml) {
        return register(fsCode, fsJrxml, (loPO, lnRow) -> text(loPO.Detail(lnRow).Inventory().getDescription()), null);
    }

    public PurchaseOrderPdfExporter register(String fsCode, String fsJrxml, DescriptionBuilder foDescription) {
        return register(fsCode, fsJrxml, foDescription, null);
    }

    public PurchaseOrderPdfExporter register(String fsCode, String fsJrxml,
                                             DescriptionBuilder foDescription, ExtraParameters foExtra) {
        poFormats.put(fsCode, new Format(fsJrxml, foDescription, foExtra));
        return this;
    }

    public boolean hasFormat(String fsCode) {
        return poFormats.containsKey(fsCode);
    }

    // =====================================================================
    // Export
    // =====================================================================
    /**
     * Exports the loaded PO in the layout to configPath/temp/{PO no}.pdf.
     * The result carries "fileName" (for Send_Mail_Master.sAttached) and
     * "path". An existing file of the same PO is replaced.
     */
    public JSONObject export(String fsFormatCode) {
        try {
            JasperPrint loPrint = build(fsFormatCode);
            if (loPrint == null) {
                return error("Unknown PDF format: " + fsFormatCode);
            }

            String lsFileName = poPO.Master().getTransactionNo() + ".pdf";
            File loFolder = new File(configPath() + "/temp");
            if (!loFolder.exists() && !loFolder.mkdirs()) {
                return error("Unable to create the folder " + loFolder.getPath());
            }

            File loFile = new File(loFolder, lsFileName);
            JasperExportManager.exportReportToPdfFile(loPrint, loFile.getPath());
            if (!loFile.exists() || loFile.length() == 0) {
                return error("The purchase order PDF was not created.");
            }

            JSONObject loJSON = success();
            loJSON.put("fileName", lsFileName);
            loJSON.put("path", loFile.getPath());
            return loJSON;
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Purchase order PDF export failed", ex);
            return error("Unable to export the purchase order to PDF.");
        }
    }

    /** Fills the layout with the loaded PO; null when the code is not registered. */
    public JasperPrint build(String fsFormatCode) throws Exception {
        Format loFormat = poFormats.get(fsFormatCode);
        if (loFormat == null) {
            return null;
        }

        List<Map<String, Object>> laRows = buildRows(loFormat);
        Map<String, Object> laParams = buildParameters(laRows);
        if (loFormat.extra != null) {
            loFormat.extra.add(poPO, laParams);
        }

        return JasperFillManager.fillReport(
                PurchaseOrderPrinter.compile(configPath() + "/reports/" + loFormat.jrxml),
                laParams, new JRMapCollectionDataSource(new ArrayList<Map<String, ?>>(laRows)));
    }

    // =====================================================================
    // Report parameters (the ones PurchaseOrderMPToEmail.jrxml declares)
    // =====================================================================
    private Map<String, Object> buildParameters(List<Map<String, Object>> faRows) throws Exception {
        Model_PO_Master loMaster = poPO.Master();
        Map<String, Object> laParams = new HashMap<>();

        laParams.put("sBranchNm", poGRider.getBranchName());
        laParams.put("sAddressx", PurchaseOrderPrinter.address(loMaster.Company().getCompanyAddress(),
                loMaster.Company().TownCity().getDescription(),
                loMaster.Company().TownCity().Province().getDescription()));
        laParams.put("sTransNox", loMaster.getTransactionNo());
        laParams.put("sCompany", loMaster.Company().getCompanyName());
        laParams.put("sSupplier", loMaster.Supplier().getCompanyName());
        laParams.put("sDestination", loMaster.Branch().getBranchName());
        //spelled with three s's, exactly as the JRXML declares it
        laParams.put("sDestinationAddresss", PurchaseOrderPrinter.address(loMaster.Branch().getAddress(),
                loMaster.Branch().TownCity().getDescription(),
                loMaster.Branch().TownCity().Province().getDescription()));
        laParams.put("sTerm", loMaster.Term().getDescription());
        laParams.put("sRemarks", loMaster.getRemarks());
        laParams.put("dTransDte", new java.sql.Date(loMaster.getTransactionDate().getTime()));

        //prepared by (declared by the layout)
        laParams.put("sCompnyNm", "");
        JSONObject loEntry = poAudit.getEntryBy(loMaster.getTable(), loMaster.getTransactionNo());
        if (!text(loEntry.get("sCompnyNm")).isEmpty()) {
            laParams.put("sCompnyNm", "Prepared by: " + loEntry.get("sCompnyNm") + " " + text(loEntry.get("sEntryDte")));
        }

        //approvers (filled only when approved; the layout has two slots)
        for (int lnCtr = 1; lnCtr <= MAX_APPROVERS; lnCtr++) {
            laParams.put("sApprval" + lnCtr, "");
        }
        if (PurchaseOrderStatus.APPROVED.equals(loMaster.getTransactionStatus())) {
            List<String> laApprovers = poAudit.getApprovers(loMaster.getTransactionNo());
            for (int lnCtr = 0; lnCtr < Math.min(laApprovers.size(), MAX_APPROVERS); lnCtr++) {
                laParams.put("sApprval" + (lnCtr + 1), laApprovers.get(lnCtr));
            }
        }

        //total quantity printed under the item list
        double lnQtyTotal = 0.00;
        for (Map<String, Object> laRow : faRows) {
            lnQtyTotal += (Double) laRow.get("nQty");
        }
        laParams.put("nQtyTotal", lnQtyTotal);

        return laParams;
    }

    // =====================================================================
    // Item rows (the fields PurchaseOrderMPToEmail.jrxml declares)
    // =====================================================================
    /**
     * One map per item, keyed by the JRXML field names. A map is used instead
     * of a bean so the names (nRowNo, sModelCode, ...) match the layout exactly.
     * Lines without an item (the empty entry row) are skipped.
     */
    private List<Map<String, Object>> buildRows(Format foFormat) throws Exception {
        List<Map<String, Object>> laRows = new ArrayList<>();
        int lnRowNo = 0;

        for (int lnCtr = 0; lnCtr < poPO.getDetailCount(); lnCtr++) {
            Model_PO_Detail loDetail = poPO.Detail(lnCtr);
            if (text(loDetail.getStockID()).trim().isEmpty()) {
                continue;
            }

            BigDecimal lnPrice = loDetail.getUnitPrice() != null
                    ? new BigDecimal(loDetail.getUnitPrice().toString()) : BigDecimal.ZERO;
            BigDecimal lnQty = loDetail.getQuantity() != null
                    ? new BigDecimal(loDetail.getQuantity().toString()) : BigDecimal.ZERO;

            BigDecimal lnDisc = discount(loDetail);

            //model name = the layout's item name + the description typed on the PO line
            String lsName = foFormat.description.build(poPO, lnCtr);
            String lsLineDescription = text(loDetail.getDescription()).trim();
            if (!lsLineDescription.isEmpty() && !lsLineDescription.equalsIgnoreCase(lsName.trim())) {
                lsName = PurchaseOrderPrinter.join(lsName, lsLineDescription);
            }

            Map<String, Object> laRow = new HashMap<>();
            laRow.put("nRowNo", ++lnRowNo);
            laRow.put("nQty", lnQty.doubleValue());
            //CHECK: sModelCode is the item barcode; use the model's own code if the layout means that
            laRow.put("sModelCode", text(loDetail.Inventory().getBarCode()));
            laRow.put("sModelName", lsName);
            laRow.put("sColor", text(loDetail.Inventory().Color().getDescription()));
            laRow.put("nUnitPrice", lnPrice.doubleValue());
            laRow.put("nDisc", new DecimalFormat("#,##0.00").format(lnDisc));   //String field in the layout
            //total = (unit price - discount) x quantity
            laRow.put("nTotal", lnPrice.subtract(lnDisc).multiply(lnQty).doubleValue());
            laRows.add(laRow);
        }
        return laRows;
    }

    /** PO_Detail column holding the per-unit discount. CHECK: must match your PO_Detail column. */
    private static final String DISCOUNT_COLUMN = "nDiscount";

    /** Per-unit discount of a line; 0 when empty or the column is not available. */
    private static BigDecimal discount(Model_PO_Detail foDetail) {
        try {
            Object loValue = foDetail.getValue(DISCOUNT_COLUMN);
            return loValue instanceof Number ? new BigDecimal(loValue.toString()) : BigDecimal.ZERO;
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Discount column " + DISCOUNT_COLUMN + " is not available", ex);
            return BigDecimal.ZERO;
        }
    }

    private static String configPath() {
        return System.getProperty("sys.default.path.config");
    }

    private static String text(Object foValue) {
        return foValue == null ? "" : foValue.toString();
    }
}