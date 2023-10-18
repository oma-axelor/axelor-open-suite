/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2023 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.apps.stock.service.batch;

import com.axelor.apps.base.AxelorException;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.BatchRepository;
import com.axelor.apps.base.service.administration.AbstractBatch;
import com.axelor.apps.base.service.exception.TraceBackService;
import com.axelor.apps.stock.db.StockBatch;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockRules;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.db.repo.StockRulesRepository;
import com.axelor.apps.stock.service.StockRulesService;
import com.axelor.apps.stock.service.app.AppStockService;
import com.axelor.common.ObjectUtils;
import com.axelor.db.JPA;
import com.axelor.studio.db.AppStock;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.persistence.TypedQuery;

public class BatchCheckStockComplianceWithStockRules extends AbstractBatch {

  protected final StockRulesService stockRulesService;
  protected final AppStockService appStockService;

  @Inject
  public BatchCheckStockComplianceWithStockRules(
      StockRulesService stockRulesService, AppStockService appStockService) {
    this.stockRulesService = stockRulesService;
    this.appStockService = appStockService;
  }

  @Override
  protected void process() {
    LocalDateTime startDate = batch.getStartDate().toLocalDateTime();

    int offset = 0;
    List<StockLocationLine> stockLocationLines;
    while (!(stockLocationLines = getStockLocationLinesToCheck(AbstractBatch.FETCH_LIMIT, offset))
        .isEmpty()) {
      for (StockLocationLine stockLocationLine : stockLocationLines) {
        try {
          processStockLocationLineCurrentAndFutureStockCheck(stockLocationLine);
          incrementDone();
          ++offset;
        } catch (Exception e) {
          incrementAnomaly();
          ++offset;
          TraceBackService.trace(e, null, batch.getId());
        }
      }
    }

    AppStock appStock = appStockService.getAppStock();
    appStock.setLastStockComplianceCheckDateTime(startDate);
  }

  @Transactional(rollbackOn = {Exception.class})
  public void processStockLocationLineCurrentAndFutureStockCheck(
      StockLocationLine stockLocationLine) throws AxelorException {
    processStockLocationLineStockCheck(stockLocationLine, StockRulesRepository.TYPE_CURRENT);
    processStockLocationLineStockCheck(stockLocationLine, StockRulesRepository.TYPE_FUTURE);
  }

  public void processStockLocationLineStockCheck(StockLocationLine stockLocationLine, int type)
      throws AxelorException {

    Product product = stockLocationLine.getProduct();
    StockLocation stockLocation = stockLocationLine.getStockLocation();

    StockBatch stockBatch = batch.getStockBatch();
    List<StockRules> usedStockRules = stockBatch.getUsedStockRulesList();

    StockRules stockRules;
    if (ObjectUtils.isEmpty(usedStockRules)) {
      stockRules =
          stockRulesService.getStockRules(
              product, stockLocation, type, StockRulesRepository.USE_CASE_STOCK_CONTROL);
    } else {
      stockRules =
          stockRulesService.getEligibleStockRulesFromList(
              usedStockRules,
              product,
              stockLocation,
              type,
              StockRulesRepository.USE_CASE_STOCK_CONTROL);
    }

    if (stockRules == null) {
      return;
    }

    if (stockRulesService.useMinStockRules(stockLocationLine, stockRules, type)) {
      stockRulesService.processStockLocationLineNonCompliantToStockRule(
          stockRules, stockLocationLine, type);
    }
  }

  public List<StockLocationLine> getStockLocationLinesToCheck(int fetchLimit, int offset) {

    StockBatch stockBatch = batch.getStockBatch();
    List<StockRules> usedStockRules = stockBatch.getUsedStockRulesList();

    StringBuilder query = new StringBuilder();
    Map<String, Object> parameterMap = new HashMap<>();
    query.append("SELECT sll FROM StockLocationLine sll");
    query.append(" INNER JOIN StockMove sm ON sll.stockLocation = sm.fromStockLocation");
    query.append(
        " INNER JOIN StockMoveLine sml ON (sm.id = sml.stockMove.id AND sll.product = sml.product)");
    query.append(" WHERE sm.statusSelect = :realizedStatusSelect");
    parameterMap.put("realizedStatusSelect", StockMoveRepository.STATUS_REALIZED);

    if (stockBatch.getRunFromLastExecution()) {
      AppStock appStock = appStockService.getAppStock();
      // TODO check if you should keep this field here ?
      LocalDateTime lastSuccessfulBatchExecDateTime =
          appStock.getLastStockComplianceCheckDateTime();
      query.append(" AND sm.deliveredAtDateTime > :lastSuccessfulBatchExecDateTime");
      parameterMap.put("lastSuccessfulBatchExecDateTime", lastSuccessfulBatchExecDateTime);
    }

    /* We can't add stockBatch.usedStockRulesList product stockLocation pair to the filter because we also want to have the ability to use rules that are linked to the parents stockLocations  */
    if (!ObjectUtils.isEmpty(usedStockRules)) {
      query.append(" AND sml.product.id IN :productIds");
      parameterMap.put(
          "productIds",
          usedStockRules.stream()
              .map(StockRules::getProduct)
              .map(Product::getId)
              .distinct()
              .collect(Collectors.toList()));
    }

    TypedQuery<StockLocationLine> typedQuery =
        JPA.em()
            .createQuery(query.toString(), StockLocationLine.class)
            .setMaxResults(fetchLimit)
            .setFirstResult(offset);
    parameterMap.forEach(typedQuery::setParameter);
    return typedQuery.getResultList();
  }

  @Override
  protected void setBatchTypeSelect() {
    this.batch.setBatchTypeSelect(BatchRepository.BATCH_TYPE_STOCK_BATCH);
  }
}
