import { useState, useEffect, useCallback } from "react";
import {
  getTodayForecast,
  getProcurementPlan,
  getProductionPlan,
  getInventory,
  getDailyAccuracy,
  submitEndOfDay,
} from "../api/api";

/* ── colour tokens ────────────────────────────────────────────── */

const URGENCY_META = {
  CRITICAL: { label: "Critical", cls: "badge-low" },
  HIGH:     { label: "High",     cls: "badge-medium" },
  MEDIUM:   { label: "Medium",   cls: "badge-high" },
  LOW:      { label: "Low",      cls: "badge-high" },
};

const VERDICT_META = {
  ACCURATE:        { label: "Accurate",        style: { background: "#dcfce7", color: "#16a34a" } },
  OVER_PREDICTED:  { label: "Over-predicted",  style: { background: "#fef9c3", color: "#a16207" } },
  UNDER_PREDICTED: { label: "Under-predicted", style: { background: "#fee2e2", color: "#dc2626" } },
};

/* ── reusable atoms ───────────────────────────────────────────── */

function Badge({ urgency }) {
  const m = URGENCY_META[urgency] || URGENCY_META.LOW;
  return <span className={`badge ${m.cls}`}>{m.label}</span>;
}

function VerdictBadge({ verdict }) {
  const m = VERDICT_META[verdict] || VERDICT_META.ACCURATE;
  return <span className="badge" style={m.style}>{m.label}</span>;
}

function KpiCard({ label, value, sub, color }) {
  const colorMap = { red: "red", orange: "orange", blue: "blue", green: "green" };
  const cls = colorMap[color] || "blue";
  return (
    <div className={`kpi-card ${cls}`}>
      <div className="kpi-label">{label}</div>
      <div className="kpi-value">{value}</div>
      {sub && <div className="kpi-sub">{sub}</div>}
    </div>
  );
}

function SectionHeader({ title, count }) {
  return (
    <div style={{ display: "flex", alignItems: "baseline", gap: 8, margin: "20px 0 10px" }}>
      <h3 style={{ margin: 0, fontSize: 14, fontWeight: 600, color: "var(--text-primary)" }}>{title}</h3>
      {count != null && (
        <span style={{ fontSize: 11, color: "var(--text-muted)", background: "#f1f5f9", borderRadius: 10, padding: "1px 8px", fontWeight: 600 }}>
          {count}
        </span>
      )}
    </div>
  );
}

function EmptyState({ message }) {
  return (
    <div style={{
      textAlign: "center", padding: "40px 16px",
      color: "var(--text-muted)", fontSize: 13,
      border: "1.5px dashed var(--border)",
      borderRadius: 12,
    }}>
      {message}
    </div>
  );
}

function Spinner() {
  return (
    <div className="loading">
      <div className="spinner" />
      Loading data…
    </div>
  );
}

function StockBar({ current, needed }) {
  const pct = needed > 0 ? Math.min(100, Math.round((current / needed) * 100)) : 100;
  const color = pct < 50 ? "var(--red)" : pct < 80 ? "var(--orange)" : "var(--green)";
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
      <div style={{ flex: 1, height: 6, background: "#e2e8f0", borderRadius: 3, overflow: "hidden" }}>
        <div style={{ width: `${pct}%`, height: "100%", background: color, borderRadius: 3, transition: "width 0.4s" }} />
      </div>
      <span style={{ fontSize: 11, color: "var(--text-muted)", minWidth: 32, textAlign: "right", fontFamily: "'DM Mono', monospace", fontWeight: 600 }}>{pct}%</span>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════
 *  MAIN COMPONENT
 * ═══════════════════════════════════════════════════════════════ */

export default function SupplyChain() {
  const [tab, setTab] = useState("procurement");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const [procurement, setProcurement] = useState(null);
  const [production, setProduction]   = useState(null);
  const [inventory, setInventory]     = useState(null);
  const [accuracy, setAccuracy]       = useState(null);

  const [predMap, setPredMap]   = useState({});
  const [inputMap, setInputMap] = useState({});
  const [submitted, setSubmitted] = useState(false);
  const [eodResult, setEodResult] = useState(null);

  const today = new Date().toISOString().split("T")[0];

  const fetchForecastAndRun = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const forecastData = await getTodayForecast();

      const qtyMap = {};
      const predictions = Array.isArray(forecastData) ? forecastData
                        : (forecastData?.predictions || forecastData || []);
      predictions.forEach(p => {
        const id  = p.itemId ?? p.menuItemId;
        const qty = p.predictedQty ?? p.quantity ?? 0;
        if (id != null) qtyMap[Number(id)] = Number(qty);
      });
      setPredMap(qtyMap);

      if (Object.keys(qtyMap).length === 0) {
        setError("No forecast data returned — make sure the forecast table has data or the ML service is reachable.");
        setLoading(false);
        return;
      }

      const results = await Promise.allSettled([
        getProcurementPlan(qtyMap),
        getProductionPlan(qtyMap),
        getInventory(),
        getDailyAccuracy(today).catch(() => []),
      ]);

      const [procResult, prodResult, invResult, accResult] = results;

      if (procResult.status === "fulfilled") setProcurement(procResult.value);
      if (prodResult.status === "fulfilled") setProduction(prodResult.value);
      if (invResult.status  === "fulfilled") setInventory(invResult.value);
      if (accResult.status  === "fulfilled") setAccuracy(accResult.value);

      const prodData  = prodResult.status === "fulfilled" ? prodResult.value : null;
      const initActuals = {};
      const prodItems = prodData?.items || (Array.isArray(prodData) ? prodData : []);
      prodItems.forEach(it => {
        initActuals[it.menuItemId] = it.actualProduce ?? it.predictedQty ?? 0;
      });
      setInputMap(initActuals);

      const failedCalls = [
        procResult.status === "rejected" ? "Procurement" : null,
        prodResult.status === "rejected" ? "Production"  : null,
        invResult.status  === "rejected" ? "Inventory"   : null,
      ].filter(Boolean);

      if (failedCalls.length > 0 && failedCalls.length < 3) {
        setError(`Partial load: ${failedCalls.join(", ")} failed. Other tabs may still work.`);
      } else if (failedCalls.length === 3) {
        setError("Could not load supply chain data. Check that your Spring Boot server is running on port 8080.");
      }

    } catch (e) {
      setError("Could not load supply chain data. Check that your Spring Boot server is running on port 8080.");
    } finally {
      setLoading(false);
    }
  }, [today]);

  useEffect(() => { fetchForecastAndRun(); }, [fetchForecastAndRun]);

  const submitEod = async () => {
    setLoading(true);
    try {
      const data = await submitEndOfDay(today, inputMap);
      setEodResult(data);
      setSubmitted(true);
      setAccuracy(data.itemBreakdown);
    } catch (e) {
      setError("Failed to submit end-of-day feedback.");
    } finally {
      setLoading(false);
    }
  };

  const procItems      = procurement?.items || (Array.isArray(procurement) ? procurement : []);
  const prodItems      = production?.items  || (Array.isArray(production)  ? production  : []);
  const criticalCount  = procItems.filter(i => i.urgency === "CRITICAL").length;
  const shortfallCount = procItems.filter(i => i.shortfall > 0).length;
  const cappedCount    = prodItems.filter(i => i.limitingFactor !== "NONE").length;
  const dailyMape      = eodResult?.dailyMape ?? accuracy?.dailyMape;

  const tabs = [
    { key: "procurement", label: "Procurement plan" },
    { key: "production",  label: "Production plan"  },
    { key: "inventory",   label: "Inventory"         },
    { key: "feedback",    label: "Feedback loop"     },
  ];

  return (
    <div>
      {/* ── KPI row ──────────────────────────────────────────── */}
      <div className="kpi-grid" style={{ gridTemplateColumns: dailyMape != null ? "repeat(4,1fr)" : "repeat(3,1fr)" }}>
        <KpiCard
          label="Critical shortfalls"
          value={criticalCount}
          sub="need immediate action"
          color={criticalCount > 0 ? "red" : "green"}
        />
        <KpiCard label="Items to procure"  value={shortfallCount} sub="below stock threshold" color="orange" />
        <KpiCard label="Production capped" value={cappedCount}    sub="ingredient or stall limit" color="blue" />
        {dailyMape != null && (
          <KpiCard label="Today's MAPE" value={`${Number(dailyMape).toFixed(1)}%`} sub="forecast accuracy error" color="green" />
        )}
      </div>

      {/* ── Error banner ─────────────────────────────────────── */}
      {error && (
        <div className="alert-banner">
          <span className="alert-banner-text">⚠ {error}</span>
          <button className="alert-banner-dismiss" onClick={() => setError(null)}>Dismiss</button>
        </div>
      )}

      {/* ── Tabs + refresh ───────────────────────────────────── */}
      <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 20 }}>
        <div className="filter-tabs" style={{ marginBottom: 0, flex: 1 }}>
          {tabs.map(t => (
            <button
              key={t.key}
              className={`tab${tab === t.key ? " active" : ""}`}
              onClick={() => setTab(t.key)}
            >
              {t.label}
            </button>
          ))}
        </div>
        <button className="btn btn-outline" style={{ fontSize: 12, padding: "5px 14px" }} onClick={fetchForecastAndRun}>
          ↻ Refresh
        </button>
      </div>

      {/* ── Content ──────────────────────────────────────────── */}
      {loading && <Spinner />}

      {!loading && tab === "procurement" && <ProcurementTab data={procurement} />}
      {!loading && tab === "production"  && <ProductionTab  data={production}  />}
      {!loading && tab === "inventory"   && <InventoryTab   data={inventory} procurement={procurement} />}
      {!loading && tab === "feedback"    && (
        <FeedbackTab
          production={production}
          inputMap={inputMap}
          setInputMap={setInputMap}
          submitted={submitted}
          eodResult={eodResult}
          accuracy={accuracy}
          onSubmit={submitEod}
        />
      )}
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════
 *  TAB: Procurement
 * ═══════════════════════════════════════════════════════════════ */

function ProcurementTab({ data }) {
  const items     = data?.items || (Array.isArray(data) ? data : []);
  const criticals = items.filter(i => i.urgency === "CRITICAL" || i.urgency === "HIGH");
  const others    = items.filter(i => i.urgency !== "CRITICAL" && i.urgency !== "HIGH");

  if (!items.length) return <EmptyState message="No procurement items — stock levels look good for tomorrow." />;

  const totalCost = items.reduce((s, i) => s + (Number(i.estimatedCost) || 0), 0);

  return (
    <div>
      <p style={{ margin: "0 0 16px", fontSize: 13, color: "var(--text-secondary)" }}>
        {items.length} ingredients to procure ·{" "}
        <strong style={{ color: "var(--text-primary)" }}>
          est. cost ₹{Math.round(totalCost).toLocaleString("en-IN")}
        </strong>
      </p>

      {criticals.length > 0 && (
        <>
          <SectionHeader title="Urgent orders" count={criticals.length} />
          <ProcurementTable items={criticals} />
        </>
      )}
      {others.length > 0 && (
        <>
          <SectionHeader title="Standard orders" count={others.length} />
          <ProcurementTable items={others} />
        </>
      )}
    </div>
  );
}

function ProcurementTable({ items }) {
  return (
    <div className="card" style={{ padding: 0, overflow: "hidden", marginBottom: 16 }}>
      <table>
        <thead>
          <tr>
            <th style={{ width: "26%" }}>Ingredient</th>
            <th style={{ width: "14%" }}>Urgency</th>
            <th style={{ width: "14%", textAlign: "right" }}>Buy qty</th>
            <th style={{ width: "15%", textAlign: "right" }}>Est. cost</th>
            <th style={{ width: "16%" }}>Supplier</th>
            <th style={{ width: "15%" }}>Delivery</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item, i) => (
            <tr key={item.ingredientName + i}>
              <td className="fw6">{item.ingredientName}</td>
              <td><Badge urgency={item.urgency} /></td>
              <td style={{ textAlign: "right", fontFamily: "'DM Mono', monospace", fontSize: 12 }}>
                {Number(item.buyQty).toFixed(2)} {item.unit}
              </td>
              <td style={{ textAlign: "right", fontFamily: "'DM Mono', monospace", fontSize: 12 }}>
                {Number(item.estimatedCost) > 0
                  ? `₹${Math.round(item.estimatedCost).toLocaleString("en-IN")}`
                  : <span className="muted">in-house</span>}
              </td>
              <td style={{ color: "var(--text-muted)", fontSize: 12 }}>{item.supplierName || "—"}</td>
              <td style={{ color: "var(--text-muted)", fontSize: 12 }}>{item.expectedDelivery || "same day"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════
 *  TAB: Production Plan
 * ═══════════════════════════════════════════════════════════════ */

function ProductionTab({ data }) {
  const items = data?.items || (Array.isArray(data) ? data : []);
  const byStall = {};
  items.forEach(it => {
    const s = it.stallName || `Stall ${it.stallId}`;
    if (!byStall[s]) byStall[s] = [];
    byStall[s].push(it);
  });

  if (!items.length) return <EmptyState message="No production plan data available." />;

  return (
    <div>
      <p style={{ margin: "0 0 16px", fontSize: 13, color: "var(--text-secondary)" }}>
        Planned output for all stalls · <strong style={{ color: "var(--text-primary)" }}>{items.length} items</strong>
      </p>
      {Object.entries(byStall).map(([stall, stallItems]) => (
        <div key={stall} style={{ marginBottom: 24 }}>
          <SectionHeader title={stall} count={stallItems.length} />
          <div className="card" style={{ padding: 0, overflow: "hidden" }}>
            <table>
              <thead>
                <tr>
                  <th style={{ width: "30%" }}>Item</th>
                  <th style={{ width: "15%", textAlign: "right" }}>Predicted</th>
                  <th style={{ width: "15%", textAlign: "right" }}>Produce</th>
                  <th style={{ width: "20%" }}>Limiting factor</th>
                  <th style={{ width: "20%" }}>Bottleneck</th>
                </tr>
              </thead>
              <tbody>
                {stallItems.map((it, i) => {
                  const capped = it.limitingFactor && it.limitingFactor !== "NONE";
                  return (
                    <tr key={it.menuItemId + i} style={{ background: capped ? "#fff7ed" : undefined }}>
                      <td className="fw6">{it.menuItemName}</td>
                      <td style={{ textAlign: "right", color: "var(--text-muted)", fontFamily: "'DM Mono', monospace", fontSize: 12 }}>{it.predictedQty}</td>
                      <td style={{ textAlign: "right", fontFamily: "'DM Mono', monospace", fontSize: 12, fontWeight: capped ? 700 : 400, color: capped ? "#c2410c" : "var(--text-primary)" }}>
                        {it.actualProduce}
                      </td>
                      <td style={{ fontSize: 12 }}>
                        {capped
                          ? <span className="badge" style={{ background: "#fff7ed", color: "#c2410c" }}>{it.limitingFactor?.replace("_", " ").toLowerCase()}</span>
                          : <span className="muted">none</span>
                        }
                      </td>
                      <td style={{ fontSize: 12, color: "var(--text-muted)" }}>{it.ingredientBottleneck || "—"}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      ))}
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════
 *  TAB: Inventory
 * ═══════════════════════════════════════════════════════════════ */

function InventoryTab({ data, procurement }) {
  const items = Array.isArray(data) ? data : (data?.items || []);
  const neededMap = {};
  const procItems = procurement?.items || (Array.isArray(procurement) ? procurement : []);
  procItems.forEach(p => { neededMap[p.ingredientName] = Number(p.neededToday) || 0; });

  const low = items.filter(i => {
    const needed     = neededMap[i.ingredientName] || 0;
    const bufferPct  = Number(i.safetyBufferPct) || 10;
    const reorderLvl = needed > 0 ? needed * (1 + bufferPct / 100) : 1;
    return Number(i.currentQty) <= reorderLvl && needed > 0;
  });
  const adequate = items.filter(i => !low.includes(i));

  if (!items.length) return <EmptyState message="No inventory data found. Add ingredients through the Inventory API." />;

  return (
    <div>
      <p style={{ margin: "0 0 16px", fontSize: 13, color: "var(--text-secondary)" }}>
        {items.length} ingredients tracked ·{" "}
        <span className="text-red fw6">{low.length} below reorder point</span>
      </p>

      {low.length > 0 && (
        <>
          <SectionHeader title="Below reorder point" count={low.length} />
          <InventoryTable items={low} neededMap={neededMap} />
        </>
      )}
      {adequate.length > 0 && (
        <>
          <SectionHeader title="Adequate stock" count={adequate.length} />
          <InventoryTable items={adequate} neededMap={neededMap} />
        </>
      )}
    </div>
  );
}

function InventoryTable({ items, neededMap }) {
  return (
    <div className="card" style={{ padding: 0, overflow: "hidden", marginBottom: 16 }}>
      <table>
        <thead>
          <tr>
            <th style={{ width: "28%" }}>Ingredient</th>
            <th style={{ width: "20%", textAlign: "right" }}>Current stock</th>
            <th style={{ width: "20%", textAlign: "right" }}>Today's need</th>
            <th style={{ width: "32%" }}>Coverage</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item, i) => {
            const needed = neededMap[item.ingredientName] || 0;
            return (
              <tr key={item.ingredientName + i}>
                <td className="fw6">{item.ingredientName}</td>
                <td style={{ textAlign: "right", fontFamily: "'DM Mono', monospace", fontSize: 12 }}>
                  {Number(item.currentQty).toFixed(2)} {item.unit}
                </td>
                <td style={{ textAlign: "right", fontFamily: "'DM Mono', monospace", fontSize: 12, color: "var(--text-muted)" }}>
                  {needed > 0 ? `${needed.toFixed(2)} ${item.unit}` : "—"}
                </td>
                <td style={{ paddingTop: 10, paddingBottom: 10 }}>
                  {needed > 0
                    ? <StockBar current={Number(item.currentQty)} needed={needed} />
                    : <span className="muted fs11">not needed today</span>
                  }
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

/* ═══════════════════════════════════════════════════════════════
 *  TAB: Feedback Loop
 * ═══════════════════════════════════════════════════════════════ */

function FeedbackTab({ production, inputMap, setInputMap, submitted, eodResult, accuracy, onSubmit }) {
  const items     = production?.items || (Array.isArray(production) ? production : []);
  const breakdown = Array.isArray(accuracy) ? accuracy : (accuracy?.itemBreakdown || []);

  if (submitted && eodResult) {
    return (
      <div>
        {/* success banner */}
        <div style={{
          background: "#f0fdf4", border: "1.5px solid #bbf7d0",
          borderRadius: 12, padding: "16px 20px", marginBottom: 24,
        }}>
          <p style={{ margin: 0, fontWeight: 700, fontSize: 14, color: "#166534" }}>
            ✓ End-of-day submitted
          </p>
          <p style={{ margin: "4px 0 0", fontSize: 13, color: "#16a34a" }}>
            {eodResult.itemsProcessed} items processed · MAPE {Number(eodResult.dailyMape).toFixed(1)}% · inventory updated
          </p>
        </div>

        {/* eod summary cards */}
        <div className="eod-summary">
          <div className="eod-card">
            <div className="eod-card-value text-green">{eodResult.itemsProcessed}</div>
            <div className="eod-card-label">items processed</div>
          </div>
          <div className="eod-card">
            <div className="eod-card-value">{Number(eodResult.dailyMape).toFixed(1)}%</div>
            <div className="eod-card-label">daily MAPE</div>
          </div>
          <div className="eod-card">
            <div className="eod-card-value text-blue">{breakdown.filter(i => i.verdict === "ACCURATE").length}</div>
            <div className="eod-card-label">accurate items</div>
          </div>
          <div className="eod-card">
            <div className="eod-card-value text-red">{breakdown.filter(i => i.verdict !== "ACCURATE").length}</div>
            <div className="eod-card-label">off-target items</div>
          </div>
        </div>

        {breakdown.length > 0 && (
          <>
            <SectionHeader title="Accuracy breakdown" count={breakdown.length} />
            <AccuracyTable items={breakdown} />
          </>
        )}
      </div>
    );
  }

  if (breakdown.length > 0 && !submitted) {
    return (
      <div>
        <p style={{ margin: "0 0 16px", fontSize: 13, color: "var(--text-secondary)" }}>
          Showing previous accuracy log. Enter today's actuals below and submit EOD when the day is done.
        </p>
        <SectionHeader title="Previous accuracy" count={breakdown.length} />
        <AccuracyTable items={breakdown} />
        <ActualsForm items={items} inputMap={inputMap} setInputMap={setInputMap} onSubmit={onSubmit} />
      </div>
    );
  }

  return (
    <div>
      <p style={{ margin: "0 0 20px", fontSize: 13, color: "var(--text-secondary)" }}>
        Enter actual sales quantities at end of day. This updates the inventory, accuracy log, and feeds the ML retraining signal.
      </p>
      <ActualsForm items={items} inputMap={inputMap} setInputMap={setInputMap} onSubmit={onSubmit} />
    </div>
  );
}

function AccuracyTable({ items }) {
  return (
    <div className="card" style={{ padding: 0, overflow: "hidden", marginBottom: 20 }}>
      <table>
        <thead>
          <tr>
            <th style={{ width: "32%" }}>Item</th>
            <th style={{ width: "14%", textAlign: "right" }}>Predicted</th>
            <th style={{ width: "14%", textAlign: "right" }}>Actual</th>
            <th style={{ width: "16%", textAlign: "right" }}>Error</th>
            <th style={{ width: "24%" }}>Verdict</th>
          </tr>
        </thead>
        <tbody>
          {items.map((it, i) => {
            const errPct = Math.abs(Number(it.absErrorPct ?? 0));
            return (
              <tr key={(it.menuItemId || it.menuItemName) + i}>
                <td className="fw6">{it.menuItemName}</td>
                <td style={{ textAlign: "right", color: "var(--text-muted)", fontFamily: "'DM Mono', monospace", fontSize: 12 }}>
                  {it.predicted ?? it.predictedQty}
                </td>
                <td style={{ textAlign: "right", fontFamily: "'DM Mono', monospace", fontSize: 12 }}>
                  {it.actual ?? it.actualQty}
                </td>
                <td style={{ textAlign: "right", fontFamily: "'DM Mono', monospace", fontSize: 12, color: errPct > 10 ? "var(--red)" : "var(--text-muted)", fontWeight: errPct > 10 ? 700 : 400 }}>
                  {errPct.toFixed(1)}%
                </td>
                <td><VerdictBadge verdict={it.verdict} /></td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function ActualsForm({ items, inputMap, setInputMap, onSubmit }) {
  if (!items.length) return <EmptyState message="Run today's forecast first to populate this form." />;

  return (
    <div>
      <SectionHeader title="Enter today's actuals" count={items.length} />
      <div className="card" style={{ padding: 0, overflow: "hidden", marginBottom: 20 }}>
        <table>
          <thead>
            <tr>
              <th style={{ width: "40%" }}>Item</th>
              <th style={{ width: "20%", textAlign: "right" }}>Planned</th>
              <th style={{ width: "40%", textAlign: "right" }}>Actual sold</th>
            </tr>
          </thead>
          <tbody>
            {items.map((it, i) => (
              <tr key={it.menuItemId + i}>
                <td className="fw6">{it.menuItemName}</td>
                <td style={{ textAlign: "right", color: "var(--text-muted)", fontFamily: "'DM Mono', monospace", fontSize: 12 }}>
                  {it.actualProduce}
                </td>
                <td style={{ textAlign: "right", padding: "6px 12px" }}>
                  <input
                    type="number"
                    min={0}
                    step={1}
                    className="sales-input"
                    style={{ width: 90 }}
                    value={inputMap[it.menuItemId] ?? ""}
                    onChange={e => setInputMap(prev => ({ ...prev, [it.menuItemId]: parseInt(e.target.value) || 0 }))}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="btn-group">
        <button className="btn btn-success" onClick={onSubmit}>
          Submit end-of-day
        </button>
        <button
          className="btn btn-outline"
          onClick={() => {
            const filled = {};
            items.forEach(it => { filled[it.menuItemId] = it.actualProduce ?? 0; });
            setInputMap(filled);
          }}
        >
          Fill from planned
        </button>
        <button
          className="btn btn-outline"
          onClick={() => {
            const zeroed = {};
            items.forEach(it => { zeroed[it.menuItemId] = 0; });
            setInputMap(zeroed);
          }}
        >
          Clear all
        </button>
      </div>
    </div>
  );
}
