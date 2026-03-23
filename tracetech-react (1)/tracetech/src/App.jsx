import React, { useState } from 'react';

import LoginPage         from './pages/LoginPage';
import Dashboard         from './pages/Dashboard';
import DemandPrediction  from './pages/DemandPrediction';
import PrepPlan          from './pages/PrepPlan';
import DynamicPricing    from './pages/DynamicPricing';
import ProfitWaste       from './pages/ProfitWaste';
import StrategySimulation from './pages/StrategySimulation';
import SalesInput        from './pages/SalesInput';

const PAGES = [
  { id: 'dashboard',   label: 'Dashboard',           icon: '⊞' },
  { id: 'demand',      label: 'Demand Prediction',   icon: '〰' },
  { id: 'prep',        label: 'Preparation Plan',    icon: '☑' },
  { id: 'pricing',     label: 'Dynamic Pricing',     icon: '◈' },
  { id: 'profit',      label: 'Profit & Waste',      icon: '+' },
  { id: 'simulation',  label: 'Strategy Simulation', icon: '⊕' },
  { id: 'sales',       label: 'Sales Input',         icon: '↓' },
];

const PAGE_TITLES = {
  dashboard:  'Dashboard',
  demand:     'Demand Prediction',
  prep:       'Preparation Plan',
  pricing:    'Dynamic Pricing',
  profit:     'Profit & Waste Analysis',
  simulation: 'Strategy Simulation',
  sales:      'Sales Input',
};

function MainApp({ onLogout }) {
  const [page, setPage] = useState('dashboard');

  const today = new Date().toLocaleDateString('en-IN', {
    weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
  });

  const renderPage = () => {
    switch (page) {
      case 'dashboard':  return <Dashboard onNavigate={setPage} />;
      case 'demand':     return <DemandPrediction />;
      case 'prep':       return <PrepPlan />;
      case 'pricing':    return <DynamicPricing />;
      case 'profit':     return <ProfitWaste />;
      case 'simulation': return <StrategySimulation />;
      case 'sales':      return <SalesInput />;
      default:           return <Dashboard onNavigate={setPage} />;
    }
  };

  return (
    <>
      {/* ── Sidebar ── */}
      <aside className="sidebar">
        <div className="sidebar-logo">
          <h1>TraceTech</h1>
          <p>RIT Canteen · AI Forecasting</p>
        </div>

        <nav className="sidebar-nav">
          {PAGES.map((p) => (
            <div
              key={p.id}
              className={`nav-item ${page === p.id ? 'active' : ''}`}
              onClick={() => setPage(p.id)}
            >
              <span className="nav-icon">{p.icon}</span>
              {p.label}
            </div>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div className="user-label">Logged in as</div>
          <div className="user-email">{localStorage.getItem('tt_email') || 'vendor@rit.ac.in'}</div>
          <button className="logout-btn" onClick={onLogout}>Logout</button>
        </div>
      </aside>

      {/* ── Main Panel ── */}
      <div className="main">
        <div className="topbar">
          <div className="topbar-left">
            <h2>{PAGE_TITLES[page]}</h2>
            <p>{today}</p>
          </div>
          {page === 'dashboard' && (
            <button className="btn btn-primary" onClick={() => setPage('sales')}>
              Submit today's sales
            </button>
          )}
        </div>

        {renderPage()}
      </div>
    </>
  );
}

export default function App() {
  const [loggedIn, setLoggedIn] = useState(!!localStorage.getItem('tt_token'));

  const handleLogin = () => setLoggedIn(true);

  const handleLogout = () => {
    localStorage.removeItem('tt_token');
    localStorage.removeItem('tt_email');
    setLoggedIn(false);
  };

  if (!loggedIn) return <LoginPage onLogin={handleLogin} />;

  return <MainApp onLogout={handleLogout} />;
}
