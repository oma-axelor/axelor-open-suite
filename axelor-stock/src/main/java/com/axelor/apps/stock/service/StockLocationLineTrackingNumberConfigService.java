package com.axelor.apps.stock.service;

import com.axelor.apps.base.db.Product;
import java.util.List;
import java.util.Map;

public interface StockLocationLineTrackingNumberConfigService {

  List<Map<String, Object>> getStockLocationLines(Product product);
}
