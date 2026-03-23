import React, { useState, useEffect } from 'react';
import { getTomorrowForecast } from '../api/api';
import { Loading } from '../components/Shared';

const BATTER_PER_UNIT = {
  35: 0.080,  // Egg Dosa
  36: 0.100,  // Ghee Roast
  37: 0.050,  // Medhu Vadai
  38: 0.120,  // Onion Uthappam
  39: 0.080,  // Plain Dosa
  40: 0.080,  // Podi Dosa
};

const BUFFER = 1.10;

export default function PrepPlan() {
  const [forecasts, setForecasts] = useState([]);
  const [loading, setLoading]     = useState(true);

  useEffect(() => {
    getTomorrowForecast()
      .then((f) => { setForecasts(f); setLoading(false); })
      .catch(() => setLoading(false));
  }, []);

  const tonight = forecasts.filter((f) => f.cluster === 'dosa_batter');
  const morning = forecasts.filter((f) => f.cluster !== 'dosa_batter');

  const byStall = {};
  morning.forEach((f) => {
    const stallName = ['','Snacks','Juices','South Indian','Lunch'][f.stallId] || 'Other';
    if (!byStall[stallName]) byStall[stallName] = [];
    byStall[stallName].push(f);
  });

  const totalBatter = tonight.reduce((sum, f) => {
    const bpu = BATTER_PER_UNIT[f.itemId] || 0.08;
    return sum + f.predictedQty * bpu * BUFFER;
  }, 0);

  return (
    <div className="content">
      {loading ? (
        <Loading text="Loading preparation plan…" />
      ) : (
        <>
          {/* ── Tonight Section ── */}
          <div className="prep-section">
            <div className="prep-section-title prep-tonight">
              🌙 Prepare Tonight — Fermentation Required
            </div>
            {tonight.map((f) => {
              const bpu = BATTER_PER_UNIT[f.itemId] || 0.08;
              const kg  = (f.predictedQty * bpu * BUFFER).toFixed(2);
              return (
                <div className="prep-item" key={f.itemId}>
                  <div>
                    <div className="prep-item-name">{f.itemName}</div>
                    <div className="prep-item-sub">
                      Tomorrow: {f.predictedQty} units · {bpu} kg/unit × {BUFFER} buffer
                    </div>
                  </div>
                  <div className="prep-item-right">
                    <div className="batter-kg">{kg} kg</div>
                    <div className="batter-label">batter needed</div>
                  </div>
                </div>
              );
            })}
          </div>

          {/* ── Morning Section ── */}
          <div className="prep-section">
            <div className="prep-section-title prep-morning">
              ☀️ Prepare Tomorrow Morning
            </div>
            {Object.entries(byStall).map(([stallName, items]) => (
              <div key={stallName} style={{ marginBottom: 16 }}>
                <div style={{
                  fontSize: 11, fontWeight: 700, color: 'var(--text-muted)',
                  textTransform: 'uppercase', letterSpacing: '0.6px', marginBottom: 8,
                }}>
                  {stallName}
                </div>
                {items.map((f) => (
                  <div className="prep-item" key={f.itemId}>
                    <div>
                      <div className="prep-item-name">{f.itemName}</div>
                    </div>
                    <div className="prep-item-right">
                      <div style={{ fontSize: 15, fontWeight: 700, color: '#166534', fontFamily: 'DM Mono' }}>
                        {f.predictedQty}
                      </div>
                      <div className="batter-label">units</div>
                    </div>
                  </div>
                ))}
              </div>
            ))}
          </div>

          {/* ── Summary Card ── */}
          <div className="card" style={{ background: 'linear-gradient(135deg,#0f1923,#1a2d3e)', color: '#fff', border: 'none' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 20 }}>
              <div>
                <div style={{ fontSize: 11, opacity: 0.6, textTransform: 'uppercase', letterSpacing: '0.6px' }}>
                  Total Batter Tonight
                </div>
                <div style={{ fontSize: 28, fontWeight: 700, fontFamily: 'DM Mono', color: '#fb923c', marginTop: 4 }}>
                  {totalBatter.toFixed(2)} kg
                </div>
              </div>
              <div>
                <div style={{ fontSize: 11, opacity: 0.6, textTransform: 'uppercase', letterSpacing: '0.6px' }}>
                  Items Prep Tonight
                </div>
                <div style={{ fontSize: 28, fontWeight: 700, fontFamily: 'DM Mono', color: '#fbbf24', marginTop: 4 }}>
                  {tonight.length}
                </div>
              </div>
              <div>
                <div style={{ fontSize: 11, opacity: 0.6, textTransform: 'uppercase', letterSpacing: '0.6px' }}>
                  Items Prep Morning
                </div>
                <div style={{ fontSize: 28, fontWeight: 700, fontFamily: 'DM Mono', color: '#4ade80', marginTop: 4 }}>
                  {morning.length}
                </div>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
