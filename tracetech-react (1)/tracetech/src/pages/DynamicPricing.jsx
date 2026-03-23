import React, { useState, useEffect } from 'react';
import { getTodayForecast, getMenuItems } from '../api/api';
import { Loading } from '../components/Shared';

// Elasticity by cluster (demand sensitivity to price changes)
const ELASTICITY_MAP = {
  dosa_batter:    0.3,
  hot_beverages:  0.3,
  samosa:         0.2,
  fresh_juices:   0.8,
  rice_mains:     0.5,
  noodles_pasta:  0.6,
  egg_mains:      0.4,
  cut_fruits:     0.7,
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

/**
 * Price suggestion logic:
 * - High demand + price insensitive (e < 0.5)  → UP 10%
 * - High demand + price sensitive   (e >= 0.5) → UP 5%
 * - Low demand                                 → DOWN 8%
 * - Normal                                     → No change
 */
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
  const [data, setData]     = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([getTodayForecast(), getMenuItems()])
      .then(([forecasts, menuItems]) => {
        const merged = forecasts.map((fc) => {
          const mi         = menuItems.find((m) => m.id === fc.itemId) || {};
          const elasticity = ELASTICITY_MAP[fc.cluster] || 0.5;
          const currentPrice = mi.sellingPrice || 50;
          const baseDailyQty = mi.baseDailyQty || 50;
          const { change, reason, suggestedPrice } = getSuggestion(
            fc.predictedQty, baseDailyQty, elasticity, currentPrice
          );
          return { ...fc, ...mi, elasticity, change, reason, currentPrice, suggestedPrice };
        });
        setData(merged);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, []);

  const upCount   = data.filter((d) => d.change > 0).length;
  const downCount = data.filter((d) => d.change < 0).length;
  const extraRev  = data.reduce(
    (s, d) => s + (d.change > 0 ? (d.suggestedPrice - d.currentPrice) * d.predictedQty : 0), 0
  );

  return (
    <div className="content">
      {loading ? (
        <Loading text="Computing price suggestions…" />
      ) : (
        <>
          {/* ── Summary Cards ── */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 14, marginBottom: 20 }}>
            <div className="card" style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 28, fontWeight: 700, color: 'var(--green)' }}>{upCount}</div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Price up suggestions</div>
            </div>
            <div className="card" style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 28, fontWeight: 700, color: 'var(--red)' }}>{downCount}</div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Price down suggestions</div>
            </div>
            <div className="card" style={{ textAlign: 'center' }}>
              <div style={{ fontSize: 28, fontWeight: 700, color: 'var(--blue)' }}>₹{extraRev.toLocaleString()}</div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>Est. extra revenue if applied</div>
            </div>
          </div>

          {/* ── Pricing Table ── */}
          <div className="card">
            <div style={{ overflowX: 'auto' }}>
              <table>
                <thead>
                  <tr>
                    <th>Item</th>
                    <th>Current Price</th>
                    <th>Elasticity</th>
                    <th>Suggested Price</th>
                    <th>Change %</th>
                    <th>Reason</th>
                  </tr>
                </thead>
                <tbody>
                  {data.map((d) => (
                    <tr key={d.itemId}>
                      <td className="fw6">{d.itemName}</td>
                      <td className="mono">₹{d.currentPrice}</td>
                      <td>
                        <span
                          style={{
                            fontSize: 11,
                            padding: '2px 8px',
                            borderRadius: 4,
                            background: '#f1f5f9',
                            color: 'var(--text-secondary)',
                          }}
                        >
                          {ELAS_LABEL[d.elasticity] || 'Medium'}
                        </span>
                      </td>
                      <td className="mono fw7">
                        {d.change > 0 && <span className="price-up">₹{d.suggestedPrice} ↑</span>}
                        {d.change < 0 && <span className="price-down">₹{d.suggestedPrice} ↓</span>}
                        {d.change === 0 && <span className="price-same">₹{d.suggestedPrice} →</span>}
                      </td>
                      <td className="mono fs12">
                        {d.change > 0 && <span className="text-green">+{(d.change * 100).toFixed(0)}%</span>}
                        {d.change < 0 && <span className="text-red">{(d.change * 100).toFixed(0)}%</span>}
                        {d.change === 0 && <span style={{ color: 'var(--text-muted)' }}>0%</span>}
                      </td>
                      <td className="fs11" style={{ color: 'var(--text-secondary)' }}>{d.reason}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
