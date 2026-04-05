import React, { useState, useEffect } from 'react';
import { getMenuByStall, getTodayForecast, submitBulkSales } from '../api/api';
import { Loading, Toast } from '../components/Shared';

export default function SalesInput() {
  const [stalls, setStalls]         = useState([]);
  const [forecasts, setForecasts]   = useState([]);
  const [activeStall, setActiveStall] = useState(null);
  const [salesData, setSalesData]   = useState({});   // { itemId: { prepared, sold } }
  const [date, setDate]             = useState(() => new Date().toISOString().slice(0, 10));
  const [loading, setLoading]       = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [toast, setToast]           = useState('');
  const [errors, setErrors]         = useState({});   // { itemId: errorMessage }

  useEffect(() => {
    Promise.all([getMenuByStall(), getTodayForecast()])
      .then(([s, f]) => {
        // API returns { 1: [...], 2: [...] } -- convert to array
        const stallNames = ['','Snacks','Juices','South Indian','Lunch'];
        const stallsArr = Object.entries(s).map(([id, items]) => ({
          stallId: Number(id),
          stallName: stallNames[Number(id)] || `Stall ${id}`,
          items,
        }));
        setStalls(stallsArr);
        setForecasts(f);
        setActiveStall(stallsArr[0]?.stallId || null);

        // Pre-fill prepared qty from forecast
        const init = {};
        f.forEach((fc) => {
          init[fc.itemId] = { prepared: fc.predictedQty, sold: '' };
        });
        setSalesData(init);
        setLoading(false);
      })
      .catch(() => setLoading(false));
  }, []);

  const updateField = (id, field, val) => {
    setSalesData((prev) => ({ ...prev, [id]: { ...prev[id], [field]: val } }));

    if (field === 'sold') {
      const prep = Number(salesData[id]?.prepared) || 0;
      if (Number(val) > prep) {
        setErrors((prev) => ({ ...prev, [id]: 'Sold cannot exceed prepared' }));
      } else {
        setErrors((prev) => { const n = { ...prev }; delete n[id]; return n; });
      }
    }
  };

  // Quick fill actions
  const fillAll = (mode) => {
    const newData = {};
    forecasts.forEach((f) => {
      newData[f.itemId] = {
        prepared: f.predictedQty,
        sold: mode === 'predicted' ? f.predictedQty
            : mode === 'zero' ? 0
            : f.predictedQty, // soldout
      };
    });
    setSalesData(newData);
    setErrors({});
  };

  const handleSubmit = async () => {
    if (Object.keys(errors).length > 0) {
      setToast('Fix errors before submitting');
      setTimeout(() => setToast(''), 3000);
      return;
    }

    setSubmitting(true);
    const arr = Object.entries(salesData)
      .filter(([, v]) => v.sold !== '' || Number(v.prepared) > 0)
      .map(([id, v]) => ({
        itemId: Number(id),
        saleDate: date,
        qtyPrepared: Number(v.prepared) || 0,
        qtySold:     Number(v.sold)     || 0,
      }));

    try {
      await submitBulkSales(arr);

      const totalWaste = arr.reduce((s, a) => s + (a.qtyPrepared - a.qtySold), 0);
      const totalRev   = arr.reduce((s, a) => {
        const fc = forecasts.find((f) => f.itemId === a.itemId);
        return s + a.qtySold * (fc?.sellingPrice || 0);
      }, 0);

      setToast(`Submitted! Waste: ${totalWaste} units | Revenue: Rs.${totalRev.toLocaleString()}`);
      setTimeout(() => setToast(''), 6000);
    } catch (e) {
      setToast('Submission failed. Please try again.');
      setTimeout(() => setToast(''), 4000);
    } finally {
      setSubmitting(false);
    }
  };

  // Items for active stall
  const currentStall = stalls.find((s) => s.stallId === activeStall);
  const stallItems   = currentStall?.items || [];

  // Stall-level totals
  const stallForecasts = forecasts.filter((f) => f.stallId === activeStall);
  const stallTotals = stallForecasts.reduce(
    (acc, f) => {
      const d    = salesData[f.itemId] || {};
      const prep = Number(d.prepared) || 0;
      const sold = Number(d.sold)     || 0;
      return { prepared: acc.prepared + prep, sold: acc.sold + sold, waste: acc.waste + Math.max(0, prep - sold) };
    },
    { prepared: 0, sold: 0, waste: 0 }
  );

  // End of day summary (all items, not just current stall)
  const eodSummary = {
    totalPrepared: Object.values(salesData).reduce((s, v) => s + (Number(v.prepared) || 0), 0),
    totalSold:     Object.values(salesData).reduce((s, v) => s + (Number(v.sold) || 0), 0),
    totalWaste:    Object.values(salesData).reduce((s, v) => s + Math.max(0, (Number(v.prepared) || 0) - (Number(v.sold) || 0)), 0),
    totalRevenue:  forecasts.reduce((s, f) => {
      const sold = Number(salesData[f.itemId]?.sold) || 0;
      return s + sold * (f.sellingPrice || 0);
    }, 0),
  };

  return (
    <div className="content">
      {loading ? (
        <Loading text="Loading items..." />
      ) : (
        <>
          {/* -- Date Selector -- */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 18 }}>
            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)' }}>Sales date:</div>
            <input
              type="date" value={date}
              onChange={(e) => setDate(e.target.value)}
              className="form-input" style={{ width: 170, padding: '7px 10px' }}
            />
          </div>

          {/* -- Quick Fill Buttons -- */}
          <div className="quick-fill-bar">
            <span className="quick-fill-label">Quick fill:</span>
            <button className="quick-fill-btn" onClick={() => fillAll('predicted')}>Fill all as predicted</button>
            <button className="quick-fill-btn" onClick={() => fillAll('zero')}>Clear all sold</button>
            <button className="quick-fill-btn" onClick={() => fillAll('soldout')}>Mark all as sold out</button>
          </div>

          {/* -- Stall Tabs -- */}
          <div className="stall-tabs">
            {stalls.map((s) => (
              <button
                key={s.stallId}
                className={`stall-tab ${activeStall === s.stallId ? 'active' : ''}`}
                onClick={() => setActiveStall(s.stallId)}
              >
                {s.stallName}
              </button>
            ))}
          </div>

          {/* -- Items Table -- */}
          <div className="card">
            {/* Header row */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr 120px 120px 180px',
                gap: 12,
                padding: '6px 0 10px',
                borderBottom: '2px solid var(--border)',
              }}
            >
              <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Item</div>
              <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', textAlign: 'center' }}>Qty Prepared</div>
              <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', textAlign: 'center' }}>Qty Sold</div>
              <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Waste</div>
            </div>

            {stallItems.map((item) => {
              const d     = salesData[item.id] || { prepared: '', sold: '' };
              const prep  = Number(d.prepared) || 0;
              const sold  = Number(d.sold)     || 0;
              const waste = prep - sold;
              const wasteCost = waste * (item.ingredientCostPerUnit || 0);
              const hasErr = errors[item.id];

              return (
                <div className="sales-item" key={item.id}>
                  <div>
                    <div className="sales-item-name">{item.name}</div>
                    {hasErr && (
                      <div style={{ fontSize: 11, color: 'var(--red)', marginTop: 2 }}>{hasErr}</div>
                    )}
                  </div>
                  <div>
                    <input
                      className="sales-input"
                      style={{ borderColor: hasErr ? 'var(--red)' : undefined }}
                      type="number" min={0}
                      value={d.prepared}
                      onChange={(e) => updateField(item.id, 'prepared', e.target.value)}
                      placeholder="0"
                    />
                  </div>
                  <div>
                    <input
                      className="sales-input"
                      style={{ borderColor: hasErr ? 'var(--red)' : undefined }}
                      type="number" min={0}
                      value={d.sold}
                      onChange={(e) => updateField(item.id, 'sold', e.target.value)}
                      placeholder="0"
                    />
                  </div>
                  <div>
                    {d.sold !== '' && d.prepared !== '' && (
                      waste > 0
                        ? <div className="waste-preview">{waste} wasted -- Rs.{wasteCost} lost</div>
                        : <div className="waste-ok">No waste</div>
                    )}
                  </div>
                </div>
              );
            })}

            {/* Stall Totals */}
            <div className="stall-summary">
              <div className="stall-sum-item">
                <div className="stall-sum-val">{stallTotals.prepared}</div>
                <div className="stall-sum-lbl">Total Prepared</div>
              </div>
              <div className="stall-sum-item">
                <div className="stall-sum-val text-green">{stallTotals.sold}</div>
                <div className="stall-sum-lbl">Total Sold</div>
              </div>
              <div className="stall-sum-item">
                <div className="stall-sum-val text-red">{stallTotals.waste}</div>
                <div className="stall-sum-lbl">Total Waste</div>
              </div>
            </div>
          </div>

          {/* -- End of Day Summary -- */}
          <div className="eod-summary">
            <div className="eod-card">
              <div className="eod-card-value">{eodSummary.totalPrepared}</div>
              <div className="eod-card-label">Total Prepared</div>
            </div>
            <div className="eod-card">
              <div className="eod-card-value text-green">{eodSummary.totalSold}</div>
              <div className="eod-card-label">Total Sold</div>
            </div>
            <div className="eod-card">
              <div className="eod-card-value text-red">{eodSummary.totalWaste}</div>
              <div className="eod-card-label">Total Waste</div>
            </div>
            <div className="eod-card">
              <div className="eod-card-value text-blue">Rs.{eodSummary.totalRevenue.toLocaleString()}</div>
              <div className="eod-card-label">Est. Revenue</div>
            </div>
          </div>

          {/* -- Submit Button -- */}
          <div style={{ marginTop: 10, textAlign: 'center' }}>
            <button
              className="btn btn-success"
              style={{ padding: '12px 40px', fontSize: 14 }}
              onClick={handleSubmit}
              disabled={submitting}
            >
              {submitting ? 'Submitting...' : 'Submit All Sales'}
            </button>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 8 }}>
              Tomorrow's predictions will be updated after submission
            </div>
          </div>

          <Toast message={toast} />
        </>
      )}
    </div>
  );
}
