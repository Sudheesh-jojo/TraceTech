import React, { useState, useEffect } from 'react';
import { getTodayForecast, getMenuItems } from '../api/api';
import { Loading } from '../components/Shared';

const ELASTICITY_MAP = {
  dosa_batter: 0.3,
  hot_beverages: 0.3,
  samosa: 0.2,
  fresh_juices: 0.8,
  rice_mains: 0.5,
  noodles_pasta: 0.6,
  egg_mains: 0.4,
  cut_fruits: 0.7,
  frankies_wraps: 0.5,
};

const ELAS_LABEL = {
  0.2: 'Very Low',
  0.3: 'Low',
  0.4: 'Medium-Low',
  0.5: 'Medium',
  0.6: 'Medium-High',
  0.7: 'High',
  0.8: 'High',
};



function getSuggestion(predictedQty, baseDailyQty, elasticity, currentPrice) {
  const ratio = predictedQty / baseDailyQty;
  let change = 0;
  let reason = 'Steady demand';

  if (ratio > 1.15 && elasticity < 0.5) {
    change = 0.10;
    reason = 'High demand, price insensitive';
  } else if (ratio > 1.15 && elasticity >= 0.5) {
    change = 0.05;
    reason = 'High demand, price sensitive';
  } else if (ratio < 0.85) {
    change = -0.08;
    reason = 'Low footfall today';
  }

  return {
    change,
    reason,
    suggestedPrice: Math.round(currentPrice * (1 + change)),
  };
}

export default function DynamicPricing() {
  // ── Bundle Deal Planner Constants ──
  const BASE_COMBO_DISCOUNT    = 0.05;
  const LOW_DEMAND_BONUS       = 0.03;
  const SAME_MEAL_BONUS        = 0.02;
  const COST_FLOOR_MULTIPLIER  = 1.10;
  const LOW_DEMAND_THRESHOLD   = 0.85;

  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [expandedId, setExpandedId] = useState(null);
  const [sliderValues, setSliderValues] = useState({});
  const [comboItems, setComboItems]       = useState([]);
  const [comboSearch, setComboSearch]     = useState('');

  useEffect(() => {
    Promise.all([getTodayForecast(), getMenuItems()])
      .then(([forecasts, menuItems]) => {
        const merged = forecasts.map((fc) => {
          const mi = menuItems.find((m) => m.id === fc.itemId) || {};
          const elasticity = ELASTICITY_MAP[fc.cluster] || 0.5;
          const currentPrice = mi.sellingPrice || 50;
          const baseDailyQty = mi.baseDailyQty || 50;

          const { change, reason, suggestedPrice } = getSuggestion(
            fc.predictedQty,
            baseDailyQty,
            elasticity,
            currentPrice
          );

          return {
            ...fc,
            ...mi,
            elasticity,
            change,
            reason,
            currentPrice,
            suggestedPrice,
            baseDailyQty,
          };
        });

        setData(merged);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, []);

  const upCount = data.filter((d) => d.change > 0).length;
  const downCount = data.filter((d) => d.change < 0).length;
  const extraRev = data.reduce(
    (s, d) =>
      s +
      (d.change > 0
        ? (d.suggestedPrice - d.currentPrice) * d.predictedQty
        : 0),
    0
  );

  // ── Combo computed values ──
  const selectedComboItems = data.filter(d => comboItems.includes(String(d.itemId)));

  const comboSearchResults = comboSearch.length > 1
    ? data.filter(d =>
        d.itemName.toLowerCase().includes(comboSearch.toLowerCase()) &&
        !comboItems.includes(String(d.itemId))
      ).slice(0, 6)
    : [];

  const comboCalc = (() => {
    if (selectedComboItems.length < 2) return null;

    const totalOriginal  = selectedComboItems.reduce((s, i) => s + i.sellingPrice, 0);
    const totalCost      = selectedComboItems.reduce((s, i) => s + i.ingredientCostPerUnit, 0);
    const costFloor      = totalCost * COST_FLOOR_MULTIPLIER;

    const slowItems = selectedComboItems.filter(
      i => i.predictedQty < i.baseDailyQty * LOW_DEMAND_THRESHOLD
    );

    const mealPeriods   = [...new Set(selectedComboItems.map(i => i.mealPeriod))];
    const sameMeal      = mealPeriods.length === 1;

    let discount = BASE_COMBO_DISCOUNT;
    discount    += slowItems.length * LOW_DEMAND_BONUS;
    if (sameMeal) discount += SAME_MEAL_BONUS;

    let comboPrice = Math.round(totalOriginal * (1 - discount));
    comboPrice     = Math.max(comboPrice, Math.ceil(costFloor));

    const savings        = totalOriginal - comboPrice;
    const actualDiscount = Math.round((savings / totalOriginal) * 100);

    return {
      totalOriginal,
      comboPrice,
      savings,
      actualDiscount,
      slowItems,
      sameMeal,
      costFloor: Math.ceil(costFloor),
      reasons: [
        `Base combo deal: -${Math.round(BASE_COMBO_DISCOUNT * 100)}%`,
        ...slowItems.map(i => `${i.itemName} moving slowly today: -${Math.round(LOW_DEMAND_BONUS * 100)}%`),
        ...(sameMeal ? [`All items served during ${mealPeriods[0]}: -${Math.round(SAME_MEAL_BONUS * 100)}%`] : []),
        ...(comboPrice === Math.ceil(costFloor) ? ['Cost floor applied \u2014 cannot discount further'] : []),
      ]
    };
  })();

  const comboSuggestions = (() => {
    const suggestions = [];

    const mealGroups = {};
    data.forEach(item => {
      if (!mealGroups[item.mealPeriod]) mealGroups[item.mealPeriod] = [];
      if (item.predictedQty < item.baseDailyQty * LOW_DEMAND_THRESHOLD) {
        mealGroups[item.mealPeriod].push(item);
      }
    });
    Object.entries(mealGroups).forEach(([meal, items]) => {
      if (items.length >= 2) {
        suggestions.push({
          type: 'Meal Deal',
          label: `Slow ${meal} items`,
          items: items.slice(0, 2),
          reason: 'Both moving slowly today \u2014 bundle to boost sales'
        });
      }
    });

    const stallGroups = {};
    data.forEach(item => {
      if (!stallGroups[item.stallId]) stallGroups[item.stallId] = [];
      stallGroups[item.stallId].push(item);
    });
    const stallIds = Object.keys(stallGroups);
    if (stallIds.length >= 2) {
      const pick = stallIds.slice(0, 2).map(sid =>
        stallGroups[sid].sort((a, b) => a.predictedQty - b.predictedQty)[0]
      );
      suggestions.push({
        type: 'Cross-Stall',
        label: 'Items from different stalls',
        items: pick,
        reason: 'Encourage students to explore multiple stalls'
      });
    }

    return suggestions.slice(0, 4);
  })();

  const handleSlider = (itemId, val) => {
    setSliderValues((prev) => ({ ...prev, [itemId]: val }));
  };

  // ── Combo handlers ──
  const addToCombo = (itemId) => {
    setComboItems(prev => [...prev, String(itemId)]);
    setComboSearch('');
  };

  const removeFromCombo = (itemId) => {
    setComboItems(prev => prev.filter(id => id !== String(itemId)));
  };

  const loadSuggestion = (suggestionItems) => {
    setComboItems(suggestionItems.map(i => String(i.itemId)));
  };

  const clearCombo = () => {
    setComboItems([]);
    setComboSearch('');
  };

  return (
    <div className="content modern-page">
      {loading ? (
        <Loading text="Computing price suggestions..." />
      ) : (
        <>
          {/* 🔹 Summary */}
          <div className="summary-grid">
            <div className="modern-card green">
              <h2>{upCount}</h2>
              <p>Price Increases</p>
            </div>
            <div className="modern-card red">
              <h2>{downCount}</h2>
              <p>Price Drops</p>
            </div>
            <div className="modern-card blue">
              <h2>₹{extraRev.toLocaleString()}</h2>
              <p>Extra Revenue</p>
            </div>
          </div>

          {/* 🔹 Table */}
          <div className="modern-card table-card">
            <table className="modern-table">
              <thead>
                <tr>
                  <th>Item</th>
                  <th>Price</th>
                  <th>Elasticity</th>
                  <th>Suggested</th>
                  <th>Change</th>
                  <th>Reason</th>
                </tr>
              </thead>
              <tbody>
                {data.map((d) => {
                  const isExpanded = expandedId === d.itemId;
                  const sv = sliderValues[d.itemId] || 0;

                  const newPrice = Math.round(d.currentPrice * (1 + sv / 100));
                  const newDemand = Math.round(
                    d.predictedQty * (1 - d.elasticity * (sv / 100))
                  );
                  const revenueDiff =
                    newPrice * newDemand -
                    d.currentPrice * d.predictedQty;

                  return (
                    <React.Fragment key={d.itemId}>
                      <tr
                        className="table-row"
                        onClick={() =>
                          setExpandedId(isExpanded ? null : d.itemId)
                        }
                      >
                        <td className="fw6">{d.itemName}</td>

                        <td>₹{d.currentPrice}</td>

                        <td>
                          <span className="badge">
                            {ELAS_LABEL[d.elasticity]}
                          </span>
                        </td>

                        <td>
                          <span
                            className={`badge ${
                              d.change > 0
                                ? 'up'
                                : d.change < 0
                                ? 'down'
                                : ''
                            }`}
                          >
                            ₹{d.suggestedPrice}
                          </span>
                        </td>

                        <td>
                          <span
                            className={
                              d.change > 0
                                ? 'text-green'
                                : d.change < 0
                                ? 'text-red'
                                : ''
                            }
                          >
                            {(d.change * 100).toFixed(0)}%
                          </span>
                        </td>

                        <td className="muted">{d.reason}</td>
                      </tr>

                      {/* 🔹 Expand Panel */}
                      {isExpanded && (
                        <tr>
                          <td colSpan={6}>
                            <div className="slider-panel">
                              <div className="slider-top">
                                <span>Adjust Price</span>
                                <span>{sv}%</span>
                              </div>

                              <input
                                type="range"
                                min={-20}
                                max={20}
                                value={sv}
                                onChange={(e) =>
                                  handleSlider(
                                    d.itemId,
                                    Number(e.target.value)
                                  )
                                }
                              />

                              <div
                                className={`result ${
                                  revenueDiff > 0
                                    ? 'positive'
                                    : revenueDiff < 0
                                    ? 'negative'
                                    : ''
                                }`}
                              >
                                New Price: ₹{newPrice} | Demand: {newDemand} |
                                Revenue: {revenueDiff >= 0 ? '+' : ''}
                                ₹{revenueDiff.toLocaleString()}
                              </div>
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* ── Bundle Deal Planner ── */}
          <div className="combo-builder-card">
            <div className="combo-builder-header">
              <div>
                <div className="combo-builder-title">Bundle Deal Planner</div>
                <div className="combo-builder-subtitle">
                  Create combo deals to move slow items and reduce waste
                </div>
              </div>
              {comboItems.length > 0 && (
                <button className="btn-clear-combo" onClick={clearCombo}>
                  Clear All
                </button>
              )}
            </div>

            {/* Smart Suggestions */}
            {comboSuggestions.length > 0 && (
              <div className="combo-suggestions">
                <div className="combo-suggestions-label">Suggested Combos Today</div>
                <div className="combo-suggestions-row">
                  {comboSuggestions.map((s, i) => (
                    <div
                      key={i}
                      className="combo-suggestion-card"
                      onClick={() => loadSuggestion(s.items)}
                    >
                      <div className="combo-suggestion-type">{s.type}</div>
                      <div className="combo-suggestion-label">{s.label}</div>
                      <div className="combo-suggestion-items">
                        {s.items.map(item => item.itemName).join(' + ')}
                      </div>
                      <div className="combo-suggestion-reason">{s.reason}</div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Item Selector */}
            <div className="combo-selector-row">
              <div className="combo-search-wrap">
                <input
                  type="text"
                  className="combo-search-input"
                  placeholder="Search and add items to combo..."
                  value={comboSearch}
                  onChange={e => setComboSearch(e.target.value)}
                />
                {comboSearchResults.length > 0 && (
                  <div className="combo-search-dropdown">
                    {comboSearchResults.map(item => (
                      <div
                        key={item.itemId}
                        className="combo-search-option"
                        onClick={() => addToCombo(item.itemId)}
                      >
                        <span className="combo-option-name">{item.itemName}</span>
                        <span className="combo-option-meta">
                          {['', 'Snacks', 'Juices', 'South Indian', 'Lunch'][item.stallId]}
                          &nbsp;·&nbsp;Rs.{item.sellingPrice}
                          {item.predictedQty < item.baseDailyQty * LOW_DEMAND_THRESHOLD && (
                            <span className="combo-option-slow"> · Slow today</span>
                          )}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {/* Selected Items Chips */}
            {selectedComboItems.length > 0 && (
              <div className="combo-chips-row">
                {selectedComboItems.map(item => (
                  <div key={item.itemId} className="combo-chip">
                    <span className="combo-chip-name">{item.itemName}</span>
                    <span className="combo-chip-price">Rs.{item.sellingPrice}</span>
                    <button
                      className="combo-chip-remove"
                      onClick={() => removeFromCombo(item.itemId)}
                    >
                      x
                    </button>
                  </div>
                ))}
              </div>
            )}

            {/* Combo Price Breakdown */}
            {comboCalc ? (
              <div className="combo-breakdown">
                <div className="combo-breakdown-left">
                  <div className="combo-breakdown-title">Combo Price Breakdown</div>
                  {comboCalc.reasons.map((r, i) => (
                    <div key={i} className="combo-reason-row">
                      <span className="combo-reason-dot" />
                      <span className="combo-reason-text">{r}</span>
                    </div>
                  ))}
                  <div className="combo-floor-note">
                    Cost floor: Rs.{comboCalc.costFloor} (ingredients x {COST_FLOOR_MULTIPLIER})
                  </div>
                </div>
                <div className="combo-breakdown-right">
                  <div className="combo-original-price">
                    Rs.{comboCalc.totalOriginal}
                  </div>
                  <div className="combo-final-price">
                    Rs.{comboCalc.comboPrice}
                  </div>
                  <div className="combo-savings-badge">
                    Save Rs.{comboCalc.savings} ({comboCalc.actualDiscount}% off)
                  </div>
                </div>
              </div>
            ) : (
              selectedComboItems.length === 1 && (
                <div className="combo-hint">Add at least one more item to calculate combo price</div>
              )
            )}

            {selectedComboItems.length === 0 && comboSuggestions.length === 0 && (
              <div className="combo-hint">Search for items above or wait for slow-moving items to appear as suggestions</div>
            )}
          </div>

        </>
      )}
    </div>
  );
}