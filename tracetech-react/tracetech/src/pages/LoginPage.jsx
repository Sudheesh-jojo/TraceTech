import React, { useState } from 'react';
import { login } from '../api/api';

export default function LoginPage({ onLogin }) {
  const [email, setEmail]       = useState('test@rit.ac.in');
  const [password, setPassword] = useState('test1234');
  const [error, setError]       = useState('');
  const [loading, setLoading]   = useState(false);

  const handleLogin = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await login(email, password);
      localStorage.setItem('tt_token', res.token);
      localStorage.setItem('tt_email', res.email || email);
      onLogin();
    } catch (e) {
      setError('Invalid email or password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page" style={{ width: '100%', height: '100vh' }}>
      <div className="login-card">
        <div className="login-logo">
          <div style={{ fontSize: 36, marginBottom: 8 }}></div>
          <h1>TraceTech</h1>
          <p>RIT Canteen · AI Forecasting System</p>
        </div>

        <div className="form-group">
          <label className="form-label">Email Address</label>
          <input
            className="form-input"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="vendor@rit.ac.in"
          />
        </div>

        <div className="form-group">
          <label className="form-label">Password</label>
          <input
            className="form-input"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleLogin()}
            placeholder="••••••••"
          />
        </div>

        <button
          className="btn btn-primary"
          style={{ width: '100%', padding: '11px' }}
          onClick={handleLogin}
          disabled={loading}
        >
          {loading ? 'Signing in…' : 'Login'}
        </button>

        {error && <p className="error-msg">{error}</p>}

        <p style={{ fontSize: 11, color: 'var(--text-muted)', textAlign: 'center', marginTop: 16 }}>
          Demo: test@rit.ac.in / test1234
        </p>
      </div>
    </div>
  );
}
