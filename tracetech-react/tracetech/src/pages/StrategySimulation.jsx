import React, { useState, useEffect } from 'react';
import { getMenuItems, getTodayForecast, simulateForecast } from '../api/api';
import { ConfBadge, Loading } from '../components/Shared';

const DAYS    = ['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'];
const WEATHER = ['Clear','Cloudy','Rainy','Stormy'];
const WEATHER_CODE = { Clear: 0, Cloudy: 1, Rainy: 2, Stormy: 3 };

export default function StrategySimulation() {
  const [form, setForm] = useState({
    dayOfWeek: 'Monday',
    isExamWeek: false,
    isHoliday: false,
    weather: 'Clear',
    temperature: 30,
  });

  const [normal, setNormal]       = useState([]);
  const [simulated, setSimulated] = useState([]);
  const [menuItems, setMenuItems] = useState([]);
  const [loading, setLoading]     = useState(false);
  const [ran, setRan]             = useState(false);
  const [scenarioName, setScenarioName] = useState('');
  const [savedScenarios, setSavedScenarios] = useState(() => {
    try { return JSON.parse(localStorage.getItem('tt_scenarios') || '[]'); }
    catch { return []; }
  });

  // Load normal (today's) forecast once on mount
  useEffect(() => {
    Promise.all([getTodayForecast(), getMenuItems()])
      .then(([forecastData, menuData]) => {
        setNormal(forecastData);
        setMenuItems(menuData);
      })
      .catch(() => {});
  }, []);

  const update = (key, val) => setForm((p) => ({ ...p, [key]: val }));

  const runSimulation = async () => {
    setLoading(true);
    try {
      const payload = {
        day_of_week:       DAYS.indexOf(form.dayOfWeek),
        is_exam_week:      form.isExamWeek ? 1 : 0,
        is_holiday:        form.isHoliday  ? 1 : 0,
        weather_condition: WEATHER_CODE[form.weather] ?? 0,
        temperature:       form.temperature,
      };
      const res = await simulateForecast(payload);
      setSimulated(res);
      setRan(true);
    } catch (e) {
      console.error('Simulation failed:', e);
    } finally {
      setLoading(false);
    }
  };

  const saveScenario = () => {
    if (!scenarioName.trim()) return;
    const scenarios = [...savedScenarios, {
      name: scenarioName.trim(),
      ...form,
      savedAt: new Date().toISOString(),
    }];
    setSavedScenarios(scenarios);
    localStorage.setItem('tt_scenarios', JSON.stringify(scenarios));
    setScenarioName('');
  };

  const loadScenario = (scenario) => {
    setForm({
      dayOfWeek: scenario.dayOfWeek,
      isExamWeek: scenario.isExamWeek,
      isHoliday: scenario.isHoliday,
      weather: scenario.weather,
      temperature: scenario.temperature,
    });
  };

  const deleteScenario = (idx) => {
    const scenarios = savedScenarios.filter((_, i) => i !== idx);
    setSavedScenarios(scenarios);
    localStorage.setItem('tt_scenarios', JSON.stringify(scenarios));
  };

  const totalNormal = normal.reduce((s, f) => s + f.predictedQty, 0);
  const totalSim    = simulated.reduce((s, f) => s + f.predictedQty, 0);
  const priceByItemId = menuItems.reduce((acc, item) => {
    acc[item.id] = item.sellingPrice || 0;
    return acc;
  }, {});
  const revNormal = normal.reduce((s, f) => s + f.predictedQty * (priceByItemId[f.itemId] || 0), 0);
  const revSim = simulated.reduce((s, f) => s + f.predictedQty * (priceByItemId[f.itemId] || 0), 0);

  // Risk assessment
  const overPrepRisk = simulated.reduce((sum, f) => {
    const normalItem = normal.find(n => n.itemId === f.itemId);
    const excess = f.predictedQty - (normalItem?.predictedQty || 0);
    const costPerUnit = menuItems.find(m => m.id === f.itemId)?.ingredientCostPerUnit || 5;
    return sum + (excess > 0 ? excess * costPerUnit : 0);
  }, 0);

  const upsideRevenue = simulated.reduce((sum, f) => {
    const normalItem = normal.find(n => n.itemId === f.itemId);
    const extra = f.predictedQty - (normalItem?.predictedQty || 0);
    const price = priceByItemId[f.itemId] || 0;
    return sum + (extra > 0 ? extra * price : 0);
  }, 0);

  return (
    <div className="content">
      <div style={{ display: 'grid', gridTemplateColumns: '280px 1fr', gap: 16 }}>

        {/* -- Scenario Builder -- */}
        <div className="card" style={{ height: 'fit-content', position: 'sticky', top: 0 }}>
          <div className="card-title">Scenario Builder</div>

          {/* -- Saved Scenarios -- */}
          {savedScenarios.length > 0 && (
            <div className="scenario-chips">
              {savedScenarios.map((sc, idx) => (
                <div
                  key={idx}
                  className="scenario-chip"
                  onClick={() => loadScenario(sc)}
                  title={`Load "${sc.name}"`}
                >
                  {sc.name}
                  <span
                    className="scenario-chip-del"
                    onClick={(e) => { e.stopPropagation(); deleteScenario(idx); }}
                    title="Delete"
                  >x</span>
                </div>
              ))}
            </div>
          )}

          <div className="sim-form">
            <div>
              <div className="sim-label">Day of Week</div>
              <select className="sim-select" value={form.dayOfWeek} onChange={(e) => update('dayOfWeek', e.target.value)}>
                {DAYS.map((d) => <option key={d}>{d}</option>)}
              </select>
            </div>

            <div>
              <div className="sim-label">Exam Week</div>
              <div className="toggle-group">
                <button className={`toggle-btn ${form.isExamWeek ? 'on' : ''}`}  onClick={() => update('isExamWeek', true)}>Yes</button>
                <button className={`toggle-btn ${!form.isExamWeek ? 'off' : ''}`} onClick={() => update('isExamWeek', false)}>No</button>
              </div>
            </div>

            <div>
              <div className="sim-label">Holiday</div>
              <div className="toggle-group">
                <button className={`toggle-btn ${form.isHoliday ? 'on' : ''}`}  onClick={() => update('isHoliday', true)}>Yes</button>
                <button className={`toggle-btn ${!form.isHoliday ? 'off' : ''}`} onClick={() => update('isHoliday', false)}>No</button>
              </div>
            </div>

            <div>
              <div className="sim-label">Weather</div>
              <select className="sim-select" value={form.weather} onChange={(e) => update('weather', e.target.value)}>
                {WEATHER.map((w) => <option key={w}>{w}</option>)}
              </select>
            </div>

            <div>
              <div className="sim-label">
                <span>Temperature</span>
                <span className="slider-val">{form.temperature}C</span>
              </div>
              <input
                type="range" min={20} max={45}
                value={form.temperature}
                onChange={(e) => update('temperature', Number(e.target.value))}
              />
            </div>

            <button
              className="btn btn-primary"
              style={{ width: '100%', padding: '10px' }}
              onClick={runSimulation}
              disabled={loading}
            >
              {loading ? 'Running simulation...' : '> Run Simulation'}
            </button>

            {/* -- Save Scenario -- */}
            <div className="scenario-save-row">
              <input
                type="text"
                className="scenario-name-input"
                placeholder="Scenario name..."
                value={scenarioName}
                onChange={(e) => setScenarioName(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && saveScenario()}
              />
              <button
                className="btn btn-outline"
                style={{ padding: '6px 12px', fontSize: 11 }}
                onClick={saveScenario}
                disabled={!scenarioName.trim()}
              >
                Save
              </button>
            </div>
          </div>
        </div>

        {/* -- Results -- */}
        <div>
          {!ran && !loading && (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: 300, color: 'var(--text-muted)' }}>
              <div style={{ fontSize: 36, marginBottom: 12, fontWeight: 700 }}>?</div>
              <div style={{ fontSize: 14, fontWeight: 600 }}>Set your scenario and run the simulation</div>
              <div style={{ fontSize: 12, marginTop: 6 }}>See how demand changes across all {normal.length} items</div>
            </div>
          )}

          {loading && (
            <div className="loading" style={{ flexDirection: 'column', gap: 12, height: 300 }}>
              <div className="spinner" style={{ width: 36, height: 36 }} />
              <div style={{ fontSize: 13, fontWeight: 500 }}>Running simulation across {normal.length} items...</div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Calling AI model, this takes a few seconds</div>
            </div>
          )}

          {ran && !loading && (
            <>
              {/* Summary */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 12, marginBottom: 14 }}>
                <div className="card" style={{ textAlign: 'center' }}>
                  <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>Normal qty</div>
                  <div style={{ fontSize: 22, fontWeight: 700, fontFamily: 'DM Mono' }}>{totalNormal.toLocaleString()}</div>
                </div>
                <div className="card" style={{ textAlign: 'center' }}>
                  <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>Simulated qty</div>
                  <div style={{ fontSize: 22, fontWeight: 700, fontFamily: 'DM Mono', color: totalSim > totalNormal ? 'var(--green)' : 'var(--red)' }}>
                    {totalSim.toLocaleString()}
                  </div>
                </div>
                <div className="card" style={{ textAlign: 'center' }}>
                  <div style={{ fontSize: 11, color: 'var(--text-muted)', marginBottom: 4 }}>Revenue change</div>
                  <div style={{ fontSize: 22, fontWeight: 700, fontFamily: 'DM Mono', color: revSim > revNormal ? 'var(--green)' : 'var(--red)' }}>
                    {revSim > revNormal ? '+' : ''}Rs.{Math.abs(revSim - revNormal).toLocaleString()}
                  </div>
                </div>
              </div>

              {/* Results table */}
              <div className="card">
                <div style={{ overflowX: 'auto' }}>
                  <table>
                    <thead>
                      <tr>
                        <th>Item</th>
                        <th>Normal Qty</th>
                        <th>Simulated Qty</th>
                        <th>Change</th>
                        <th>Confidence</th>
                      </tr>
                    </thead>
                    <tbody>
                      {simulated.map((f) => {
                        const normalItem = normal.find(n => n.itemId === f.itemId);
                        const normalQty  = normalItem?.predictedQty || 0;
                        const simQty     = f.predictedQty;
                        const diff = simQty - normalQty;
                        const pct  = normalQty > 0 ? ((diff / normalQty) * 100).toFixed(0) : 0;
                        return (
                          <tr key={f.itemId}>
                            <td className="fw6">{f.itemName}</td>
                            <td className="mono">{normalQty}</td>
                            <td className="mono fw7">{simQty}</td>
                            <td className="mono fw6">
                              {diff > 0  && <span className="text-green">+{pct}%</span>}
                              {diff < 0  && <span className="text-red">{pct}%</span>}
                              {diff === 0 && <span style={{ color: 'var(--text-muted)' }}>0%</span>}
                            </td>
                            <td><ConfBadge val={f.confidence} /></td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* -- Risk Assessment -- */}
              <div className="risk-grid">
                <div className="risk-card downside">
                  <div className="risk-icon">/!\ RISK</div>
                  <div className="risk-label">Over-preparation risk</div>
                  <div className="risk-value">Rs.{overPrepRisk.toLocaleString()}</div>
                  <div className="risk-desc">
                    If this scenario doesn't happen, you risk wasting Rs.{overPrepRisk.toLocaleString()} in over-prepared food
                  </div>
                </div>
                <div className="risk-card upside">
                  <div className="risk-icon">[OK] UPSIDE</div>
                  <div className="risk-label">Additional revenue potential</div>
                  <div className="risk-value">Rs.{upsideRevenue.toLocaleString()}</div>
                  <div className="risk-desc">
                    If scenario happens, additional revenue of Rs.{upsideRevenue.toLocaleString()}
                  </div>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
