import React, { useState, useEffect } from 'react';
import { getTodayForecast, getItemForecast } from '../api/api';
import { ConfBadge, Loading, MiniBarChartWidget, FilterTabs } from '../components/Shared';

const TABS     = ['All', 'Snacks', 'Juices', 'South Indian', 'Lunch'];
const STALL_MAP = { Snacks: 1, Juices: 2, 'South Indian': 3, Lunch: 4 };

export default function DemandPrediction() {
  const [forecasts, setForecasts]       = useState([]);
  const [tab, setTab]                   = useState('All');
  const [expanded, setExpanded]         = useState(null);
  const [itemForecasts, setItemForecasts] = useState({});
  const [loading, setLoading]           = useState(true);

  useEffect(() => {
    getTodayForecast()
      .then((f) => { setForecasts(f); setLoading(false); })
      .catch(() => setLoading(false));
  }, []);

  const filtered =
    tab === 'All' ? forecasts : forecasts.filter((f) => f.stallId === STALL_MAP[tab]);

  const handleRowClick = async (id) => {
    if (expanded === id) { setExpanded(null); return; }
    setExpanded(id);
    if (!itemForecasts[id]) {
      try {
        const d = await getItemForecast(id);
        setItemForecasts((prev) => ({ ...prev, [id]: d }));
      } catch {
        setItemForecasts((prev) => ({ ...prev, [id]: [] }));
      }
    }
  };

  return (
    <div className="content">
      {loading ? (
        <Loading text="Loading predictions…" />
      ) : (
        <>
          <FilterTabs tabs={TABS} active={tab} onChange={setTab} />

          <div className="card">
            <div style={{ overflowX: 'auto' }}>
              <table>
                <thead>
                  <tr>
                    <th>Item</th>
                    <th>Stall</th>
                    <th>Period</th>
                    <th>Predicted</th>
                    <th>Range</th>
                    <th>Cost if Over</th>
                    <th>Reasons</th>
                    <th>Confidence</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((f) => (
                    <React.Fragment key={f.itemId}>
                      {/* Main row — click to expand */}
                      <tr
                        style={{ cursor: 'pointer' }}
                        onClick={() => handleRowClick(f.itemId)}
                      >
                        <td className="fw6">
                          {f.anomalyFlag && (
                            <span title="Anomaly detected" style={{ marginRight: 6 }}>⚠️</span>
                          )}
                          {f.itemName}
                        </td>
                        <td className="fs12" style={{ color: 'var(--text-muted)' }}>{['','Snacks','Juices','South Indian','Lunch'][f.stallId]}</td>
                        <td className="fs11" style={{ color: 'var(--text-secondary)' }}>
                          {f.mealPeriod || 'allday'}
                        </td>
                        <td className="fw7 mono">{f.predictedQty}</td>
                        <td className="fs11 mono" style={{ color: 'var(--text-muted)' }}>
                          {f.rangeLow}–{f.rangeHigh}
                        </td>
                        <td className="text-red fw6 mono fs12">₹{f.estimatedCostIfOver}</td>
                        <td style={{ maxWidth: 200 }}>
                          {(f.topReasons || []).map((r, i) => (
                            <span
                              key={i}
                              style={{
                                display: 'inline-block',
                                background: '#f1f5f9',
                                padding: '2px 7px',
                                borderRadius: 4,
                                fontSize: 10,
                                marginRight: 4,
                                marginBottom: 2,
                                color: 'var(--text-secondary)',
                              }}
                            >
                              {r}
                            </span>
                          ))}
                        </td>
                        <td><ConfBadge val={f.confidence} /></td>
                      </tr>

                      {/* Expanded 7-day mini chart */}
                      {expanded === f.itemId && (
                        <tr className="expanded-row">
                          <td colSpan={8}>
                            <div style={{ padding: '10px 0' }}>
                              <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-secondary)', marginBottom: 8 }}>
                                7-day forecast for {f.itemName}
                              </div>
                              {itemForecasts[f.itemId]
                                ? itemForecasts[f.itemId].length > 0
                                  ? <MiniBarChartWidget data={itemForecasts[f.itemId]} />
                                  : <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>No data available</div>
                                : <Loading text="Loading…" />}
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
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
