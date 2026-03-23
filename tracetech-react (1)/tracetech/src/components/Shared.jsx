import React from 'react';
import { Line, Bar } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  CategoryScale, LinearScale, PointElement, LineElement,
  BarElement, Title, Tooltip, Legend, Filler,
} from 'chart.js';

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement,
  BarElement, Title, Tooltip, Legend, Filler);

// ── Confidence Badge ────────────────────────────────────
export function ConfBadge({ val }) {
  const cls =
    val === 'High'   ? 'badge-high' :
    val === 'Medium' ? 'badge-medium' : 'badge-low';
  return <span className={`badge ${cls}`}>{val}</span>;
}

// ── Loading Spinner ─────────────────────────────────────
export function Loading({ text = 'Loading…' }) {
  return (
    <div className="loading">
      <div className="spinner" />
      {text}
    </div>
  );
}

// ── Line Chart ──────────────────────────────────────────
export function LineChartWidget({ labels, datasets, height = 200 }) {
  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'top', labels: { font: { size: 11 }, boxWidth: 20 } } },
    scales: {
      x: { grid: { color: '#f1f5f9' }, ticks: { font: { size: 10 } } },
      y: {
        grid: { color: '#f1f5f9' },
        ticks: { font: { size: 10 }, callback: (v) => '₹' + v },
      },
    },
  };
  return (
    <div style={{ height }}>
      <Line data={{ labels, datasets }} options={options} />
    </div>
  );
}

// ── Horizontal Bar Chart ────────────────────────────────
export function HBarChartWidget({ labels, data, height = 200 }) {
  const options = {
    indexAxis: 'y',
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      x: { grid: { color: '#f1f5f9' }, ticks: { font: { size: 10 }, callback: (v) => '₹' + v } },
      y: { grid: { color: '#f1f5f9' }, ticks: { font: { size: 10 } } },
    },
  };
  const chartData = {
    labels,
    datasets: [{ data, backgroundColor: '#ef4444', borderRadius: 4 }],
  };
  return (
    <div style={{ height }}>
      <Bar data={chartData} options={options} />
    </div>
  );
}

// ── Mini Bar Chart (7-day) ──────────────────────────────
export function MiniBarChartWidget({ data }) {
  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      x: { grid: { display: false }, ticks: { font: { size: 9 } } },
      y: { grid: { color: '#f8fafc' }, ticks: { font: { size: 9 } } },
    },
  };
  const chartData = {
    labels: data.map((d) => d.day),
    datasets: [{ data: data.map((d) => d.qty), backgroundColor: '#3b82f6', borderRadius: 3 }],
  };
  return (
    <div style={{ height: 90 }}>
      <Bar data={chartData} options={options} />
    </div>
  );
}

// ── Filter Tabs ─────────────────────────────────────────
export function FilterTabs({ tabs, active, onChange }) {
  return (
    <div className="filter-tabs">
      {tabs.map((t) => (
        <button
          key={t}
          className={`tab ${active === t ? 'active' : ''}`}
          onClick={() => onChange(t)}
        >
          {t}
        </button>
      ))}
    </div>
  );
}

// ── Toast ───────────────────────────────────────────────
export function Toast({ message }) {
  if (!message) return null;
  return <div className="toast">{message}</div>;
}
