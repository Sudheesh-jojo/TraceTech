import React, { useState, useEffect, useMemo } from 'react';
import { getTodayForecast, getItemForecast } from '../api/api';
import { ConfBadge, Loading, MiniBarChartWidget, FilterTabs } from '../components/Shared';

const TABS = ['All', 'Snacks', 'Juices', 'South Indian', 'Lunch'];
const STALL_MAP = { Snacks: 1, Juices: 2, 'South Indian': 3, Lunch: 4 };

function getTrend(predictedQty, avg) {
  if (!avg) return 'stable';
  if (predictedQty > avg * 1.1) return 'up';
  if (predictedQty < avg * 0.9) return 'down';
  return 'stable';
}

function classifyItems(forecasts) {
  const avgQty = forecasts.reduce((s, f) => s + f.predictedQty, 0) / (forecasts.length || 1);
  const avgCost = forecasts.reduce((s, f) => s + (f.estimatedCostIfOver || 0), 0) / (forecasts.length || 1);

  return forecasts.reduce(
    (acc, f) => {
      const highDemand = f.predictedQty >= avgQty;
      const highWaste = (f.estimatedCostIfOver || 0) >= avgCost;

      if (highDemand && !highWaste) acc.star.push(f);
      else if (!highDemand && highWaste) acc.problem.push(f);
      else if (highDemand && highWaste) acc.opportunity.push(f);
      else acc.slow.push(f);

      return acc;
    },
    { star: [], problem: [], opportunity: [], slow: [] }
  );
}

export default function DemandPrediction() {
  const [forecasts, setForecasts] = useState([]);
  const [tab, setTab] = useState('All');
  const [expanded, setExpanded] = useState(null);
  const [itemForecasts, setItemForecasts] = useState({});
  const [compareIds, setCompareIds] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getTodayForecast()
      .then((f) => {
        setForecasts(f);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, []);

  const filtered = useMemo(
    () => (tab === 'All' ? forecasts : forecasts.filter((f) => f.stallId === STALL_MAP[tab])),
    [forecasts, tab]
  );

  const clusters = useMemo(() => classifyItems(forecasts), [forecasts]);

  const handleRowClick = async (id) => {
    if (expanded === id) return setExpanded(null);
    setExpanded(id);

    if (!itemForecasts[id]) {
      const data = await getItemForecast(id).catch(() => []);
      setItemForecasts((prev) => ({ ...prev, [id]: data }));
    }
  };

  const toggleCompare = (id, e) => {
    e.stopPropagation();
    setCompareIds((prev) => {
      if (prev.includes(id)) return prev.filter((x) => x !== id);
      if (prev.length >= 2) return [prev[1], id];
      return [...prev, id];
    });
  };

  const compareItems = compareIds.map((id) => forecasts.find((f) => f.itemId === id)).filter(Boolean);

  if (loading) return <Loading text="Loading predictions..." />;

  return (
    <div className="content">

      {/* Tabs */}
      <FilterTabs tabs={TABS} active={tab} onChange={setTab} />

      {/* Table */}
      <div className="card">
        <div className="card-title">Demand Predictions</div>

        <table>
          <thead>
            <tr>
              <th></th>
              <th>Item</th>
              <th>Stall</th>
              <th>Predicted</th>
              <th>Trend</th>
              <th>Range</th>
              <th>Cost</th>
              <th>Confidence</th>
            </tr>
          </thead>

          <tbody>
            {filtered.map((f) => {
              const trend = getTrend(f.predictedQty, f.rolling7dayAvg || 0);

              return (
                <React.Fragment key={f.itemId}>
                  <tr onClick={() => handleRowClick(f.itemId)} className="clickable-row">

                    <td onClick={(e) => e.stopPropagation()}>
                      <input
                        type="checkbox"
                        checked={compareIds.includes(f.itemId)}
                        onChange={(e) => toggleCompare(f.itemId, e)}
                      />
                    </td>

                    <td className="fw6">
                      {f.anomalyFlag && <span className="anomaly-dot" />}
                      {f.itemName}
                    </td>

                    <td className="muted">{['','Snacks','Juices','South Indian','Lunch'][f.stallId]}</td>

                    <td className="mono fw7">{f.predictedQty}</td>

                    <td>
                      {trend === 'up' && <span className="trend up">↑</span>}
                      {trend === 'down' && <span className="trend down">↓</span>}
                      {trend === 'stable' && <span className="trend">→</span>}
                    </td>

                    <td className="mono muted">{f.rangeLow}–{f.rangeHigh}</td>

                    <td className="mono text-red">₹{f.estimatedCostIfOver}</td>

                    <td><ConfBadge val={f.confidence} /></td>
                  </tr>

                  {/* Expand */}
                  {expanded === f.itemId && (
                    <tr>
                      <td colSpan={8}>
                        <div className="expand-card">
                          {itemForecasts[f.itemId]
                            ? <MiniBarChartWidget data={itemForecasts[f.itemId]} />
                            : <Loading text="Loading..." />}
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

      {/* Comparison */}
      {compareItems.length === 2 && (
        <div className="card">
          <div className="card-title">Comparison</div>

          <div className="comparison-grid">
            {['Predicted', 'Avg', 'Cost'].map((label, i) => (
              <React.Fragment key={i}>
                <div className="label">{label}</div>
                <div>{[compareItems[0].predictedQty, compareItems[0].rolling7dayAvg, compareItems[0].estimatedCostIfOver][i]}</div>
                <div>{[compareItems[1].predictedQty, compareItems[1].rolling7dayAvg, compareItems[1].estimatedCostIfOver][i]}</div>
              </React.Fragment>
            ))}
          </div>
        </div>
      )}

      {/* Clusters */}
      <div className="card">
        <div className="card-title">Item Insights</div>

        <div className="cluster-grid">
          {[
            ['Star', clusters.star],
            ['Problem', clusters.problem],
            ['Opportunity', clusters.opportunity],
            ['Slow', clusters.slow],
          ].map(([title, items]) => (
            <div key={title} className="cluster-box">
              <div className="cluster-title">{title} ({items.length})</div>
              <div className="cluster-items">
                {items.length
                  ? items.map((f) => <span key={f.itemId} className="tag">{f.itemName}</span>)
                  : <span className="muted">None</span>}
              </div>
            </div>
          ))}
        </div>
      </div>

    </div>
  );
}