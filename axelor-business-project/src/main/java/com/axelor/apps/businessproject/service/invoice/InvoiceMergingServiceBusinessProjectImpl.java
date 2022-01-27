package com.axelor.apps.businessproject.service.invoice;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.businessproject.service.PurchaseOrderInvoiceProjectServiceImpl;
import com.axelor.apps.businessproject.service.SaleOrderInvoiceProjectServiceImpl;
import com.axelor.apps.project.db.Project;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceService;
import com.axelor.apps.supplychain.service.SaleOrderInvoiceService;
import com.axelor.apps.supplychain.service.invoice.InvoiceMergingServiceSupplychainImpl;
import com.axelor.exception.AxelorException;
import com.axelor.i18n.I18n;
import com.google.inject.Inject;
import java.util.List;
import java.util.StringJoiner;

public class InvoiceMergingServiceBusinessProjectImpl extends InvoiceMergingServiceSupplychainImpl {

  private final PurchaseOrderInvoiceProjectServiceImpl purchaseOrderInvoiceProjectServiceImpl;
  private final SaleOrderInvoiceProjectServiceImpl saleOrderInvoiceProjectServiceImpl;

  @Inject
  public InvoiceMergingServiceBusinessProjectImpl(
      InvoiceService invoiceService,
      PurchaseOrderInvoiceService purchaseOrderInvoiceService,
      SaleOrderInvoiceService saleOrderInvoiceService,
      PurchaseOrderInvoiceProjectServiceImpl purchaseOrderInvoiceProjectServiceImpl,
      SaleOrderInvoiceProjectServiceImpl saleOrderInvoiceProjectServiceImpl) {
    super(invoiceService, purchaseOrderInvoiceService, saleOrderInvoiceService);
    this.purchaseOrderInvoiceProjectServiceImpl = purchaseOrderInvoiceProjectServiceImpl;
    this.saleOrderInvoiceProjectServiceImpl = saleOrderInvoiceProjectServiceImpl;
  }

  protected static class CommonFieldsBusinessProjectImpl extends CommonFieldsSupplychainImpl {
    private Project commonProject;

    public Project getCommonProject() {
      return commonProject;
    }

    public void setCommonProject(Project commonProject) {
      this.commonProject = commonProject;
    }
  }

  protected static class ChecksBusinessProjectImpl extends ChecksSupplychainImpl {
    private boolean projectIsNull;

    public boolean isProjectIsNull() {
      return projectIsNull;
    }

    public void setProjectIsNull(boolean projectIsNull) {
      this.projectIsNull = projectIsNull;
    }
  }

  protected static class InvoiceMergingResultBusinessProjectImpl
      extends InvoiceMergingResultSupplychainImpl {
    private final CommonFieldsBusinessProjectImpl commonFields;
    private final ChecksBusinessProjectImpl checks;

    public InvoiceMergingResultBusinessProjectImpl() {
      super();
      this.commonFields = new CommonFieldsBusinessProjectImpl();
      this.checks = new ChecksBusinessProjectImpl();
    }
  }

  @Override
  public InvoiceMergingResultBusinessProjectImpl create() {
    return new InvoiceMergingResultBusinessProjectImpl();
  }

  @Override
  public CommonFieldsBusinessProjectImpl getCommonFields(InvoiceMergingResult result) {
    return ((InvoiceMergingResultBusinessProjectImpl) result).commonFields;
  }

  @Override
  public ChecksBusinessProjectImpl getChecks(InvoiceMergingResult result) {
    return ((InvoiceMergingResultBusinessProjectImpl) result).checks;
  }

  @Override
  protected void fillCommonFields(Invoice invoice, InvoiceMergingResult result, int invoiceCount) {
    super.fillCommonFields(invoice, result, invoiceCount);
    if (invoiceCount == 1) {
      getCommonFields(result).setCommonProject(invoice.getProject());
      if (getCommonFields(result).getCommonProject() == null) {
        getChecks(result).setProjectIsNull(true);
      }
    } else {
      if (getCommonFields(result).getCommonProject() != null
          && !getCommonFields(result).getCommonProject().equals(invoice.getProject())) {
        getCommonFields(result).setCommonProject(null);
      }
      if (invoice.getProject() != null) {
        getChecks(result).setProjectIsNull(true);
      }
    }
  }

  @Override
  protected void checkErrors(StringJoiner fieldErrors, InvoiceMergingResult result)
      throws AxelorException {
    super.checkErrors(fieldErrors, result);
    if (getCommonFields(result).getCommonProject() == null
        && !getChecks(result).isProjectIsNull()) {
      fieldErrors.add(
          I18n.get(
              com.axelor.apps.account.exception.IExceptionMessage.INVOICE_MERGE_ERROR_PROJECT));
    }
  }

  @Override
  protected Invoice generateMergedInvoice(
      List<Invoice> invoicesToMerge, InvoiceMergingResult result) throws AxelorException {
    if (result.getInvoiceType().equals(InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE)) {
      return purchaseOrderInvoiceProjectServiceImpl.mergeInvoice(
          invoicesToMerge,
          getCommonFields(result).getCommonCompany(),
          getCommonFields(result).getCommonCurrency(),
          getCommonFields(result).getCommonPartner(),
          getCommonFields(result).getCommonContactPartner(),
          getCommonFields(result).getCommonPriceList(),
          getCommonFields(result).getCommonPaymentMode(),
          getCommonFields(result).getCommonPaymentCondition(),
          getCommonFields(result).getTradingName(),
          getCommonFields(result).getCommonSupplierInvoiceNb(),
          getCommonFields(result).getCommonOriginDate(),
          getCommonFields(result).getCommonPurchaseOrder(),
          getCommonFields(result).getCommonProject());
    }
    if (result.getInvoiceType().equals(InvoiceRepository.OPERATION_TYPE_CLIENT_SALE)) {
      return saleOrderInvoiceProjectServiceImpl.mergeInvoice(
          invoicesToMerge,
          getCommonFields(result).getCommonCompany(),
          getCommonFields(result).getCommonCurrency(),
          getCommonFields(result).getCommonPartner(),
          getCommonFields(result).getCommonContactPartner(),
          getCommonFields(result).getCommonPriceList(),
          getCommonFields(result).getCommonPaymentMode(),
          getCommonFields(result).getCommonPaymentCondition(),
          getCommonFields(result).getTradingName(),
          getCommonFields(result).getCommonSaleOrder(),
          getCommonFields(result).getCommonProject());
    }
    return null;
  }
}
