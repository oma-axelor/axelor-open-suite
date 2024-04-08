package com.axelor.apps.stock.service;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.ProductRepository;
import com.axelor.apps.base.db.repo.UnitRepository;
import com.axelor.apps.stock.db.repo.StockLocationLineRepository;
import com.axelor.apps.stock.db.repo.StockLocationRepository;
import com.axelor.db.JPA;
import com.google.inject.Inject;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StockLocationLineTrackingNumberConfigServiceImpl
    implements StockLocationLineTrackingNumberConfigService {

  protected StockLocationLineRepository stockLocationLineRepository;
  protected ProductRepository productRepository;
  protected StockLocationRepository stockLocationRepository;
  protected UnitRepository unitRepository;

  @Inject
  public StockLocationLineTrackingNumberConfigServiceImpl(
      StockLocationLineRepository stockLocationLineRepository,
      ProductRepository productRepository,
      StockLocationRepository stockLocationRepository,
      UnitRepository unitRepository) {
    this.stockLocationLineRepository = stockLocationLineRepository;
    this.productRepository = productRepository;
    this.stockLocationRepository = stockLocationRepository;
    this.unitRepository = unitRepository;
  }

  @Override
  public List<Map<String, Object>> getStockLocationLines(Product product) {

    // return (List<Map<String, Object>>)

    List<Object[]> resultList =
        JPA.em()
            .createNativeQuery(
                "SELECT "
                    + "  sll_p.stock_location,"
                    + "  sll_p.current_qty,"
                    + "  sll_p.future_qty,"
                    + "  sll_p.unit,"
                    + "  sll_p.current_qty - coalesce(SUM(sll_tn.current_qty), 0) as qtyLeft"
                    + " FROM stock_stock_location_line sll_p"
                    + "    LEFT JOIN stock_stock_location_line sll_tn ON (sll_p.stock_location = sll_tn.details_stock_location AND sll_tn.product = sll_p.product)"
                    + " WHERE sll_p.product = :productId AND sll_p.stock_location is not null GROUP BY sll_p.id,sll_p.current_qty"
                    + " HAVING sll_p.current_qty > coalesce(SUM(sll_tn.current_qty), 0)")
            .setParameter("productId", product.getId())
            .getResultList();

    List<Map<String, Object>> result = new ArrayList<>();
    for (Object[] resultLine : resultList) {
      Map<String, Object> resultMap = new HashMap<>();
      resultMap.put("product", product);
      resultMap.put(
          "stockLocation", stockLocationRepository.find(((BigInteger) resultLine[0]).longValue()));
      resultMap.put("currentQty", resultLine[1]);
      resultMap.put("futureQty", resultLine[2]);
      resultMap.put("unit", unitRepository.find(((BigInteger) resultLine[3]).longValue()));
      resultMap.put("$_qtyLeft", resultLine[4]);
      result.add(resultMap);
    }

    /*   JPA.em()
                .createQuery(
                        "SELECT "
                                + "  sll_p.product,"
                                + "  sll_p.stockLocation,"
                                + "  sll_p.currentQty,"
                                + "  sll_p.futureQty,"
                                + "  sll_p.unit,"
                                + "  sll_p.currentQty - coalesce(SUM(sll_tn.currentQty), 0) as qtyLeft"
                                + " FROM StockLocationLine sll_p"
                                + "    LEFT JOIN StockLocationLine sll_tn ON (sll_p.stockLocation = sll_tn.detailsStockLocation AND sll_tn.product = sll_p.product)"
                                + " WHERE sll_p.product = :product AND sll_p.stockLocation is not null GROUP BY sll_p.id, sll_p.product.id, sll_tn.product.id"
                                + " HAVING sll_p.currentQty > coalesce(SUM(sll_tn.currentQty), 0)")
                .setParameter("product", product)
                .getResultList();
    */
    return result;
  }
}
