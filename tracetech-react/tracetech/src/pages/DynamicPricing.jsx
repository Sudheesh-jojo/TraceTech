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

const BUNDLES = [
  { items: ['Samosa', 'Filter Coffee'], combo: 25, saving: 2, reason: 'Most popular morning combo' },
  { items: ['Plain Dosa', 'Filter Coffee'], combo: 40, saving: 5, reason: 'Classic breakfast bundle' },
  { items: ['Veg Biryani', 'Lemon Juice'], combo: 70, saving: 5, reason: 'Lunch combo deal' },
  { items: ['French Fries', 'Lemon Soda'], combo: 70, saving: 5, reason: 'Evening snack bundle' },
];

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
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [expandedId, setExpandedId] = useState(null);
  const [sliderValues, setSliderValues] = useState({});

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

  const handleSlider = (itemId, val) => {
    setSliderValues((prev) => ({ ...prev, [itemId]: val }));
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

          {/* 🔹 Bundles */}
          <div className="bundle-section">
            <h3>Bundle Opportunities</h3>
            <div className="bundle-grid">
              {BUNDLES.map((b, idx) => (
                <div className="bundle-card modern-card" key={idx}>
                  <h4>{b.items.join(' + ')}</h4>
                  <p>
                    ₹{b.combo} <span>Save ₹{b.saving}</span>
                  </p>
                  <small>{b.reason}</small>
                </div>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  );
}