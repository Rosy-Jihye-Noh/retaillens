import { useState, useEffect, useRef } from 'react';
import { PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Legend } from 'recharts';

const API = import.meta.env.VITE_API_URL || 'http://localhost:8080';
const COLORS = ['#4caf50', '#f44336', '#2196f3', '#ff9800', '#9c27b0'];

function Card({ label, value }) {
  return (
    <div style={{ border: '1px solid #ddd', borderRadius: 8, padding: 16, textAlign: 'center' }}>
      <div style={{ fontSize: 13, color: '#888' }}>{label}</div>
      <div style={{ fontSize: 28, fontWeight: 'bold', marginTop: 4 }}>{value}</div>
    </div>
  );
}

function Heatmap({ data }) {
  if (!data || !data.data) return null;
  const cells = data.data.flat();
  return (
    <div>
      <h3>고객 동선 Heatmap</h3>
      <div style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${data.grid_width}, 1fr)`,
        gap: 1, maxWidth: 640, border: '1px solid #ccc',
      }}>
        {cells.map((v, i) => (
          <div key={i} title={v.toFixed(2)} style={{
            aspectRatio: '1',
            background: `rgba(255, ${Math.round(255 * (1 - v))}, 0, ${0.15 + v * 0.85})`,
          }} />
        ))}
      </div>
      <p style={{ fontSize: 12, color: '#888' }}>빨강=사람 많음 / 노랑·투명=적음</p>
    </div>
  );
}

export default function App() {
  const [file, setFile] = useState(null);
  const [recordedAt, setRecordedAt] = useState('');
  const [job, setJob] = useState(null);
  const [stats, setStats] = useState(null);
  const [heatmap, setHeatmap] = useState(null);
  const [uploading, setUploading] = useState(false);
  const pollRef = useRef(null);

  const startPolling = (jobId) => {
    if (pollRef.current) clearInterval(pollRef.current);
    pollRef.current = setInterval(async () => {
      const res = await fetch(`${API}/jobs/${jobId}`);
      const j = await res.json();
      setJob(j);
      if (j.status === 'DONE' || j.status === 'FAILED') {
        clearInterval(pollRef.current);
        if (j.status === 'DONE') {
          const sres = await fetch(`${API}/stats/${jobId}`);
          setStats(await sres.json());
          const hres = await fetch(`${API}/jobs/${jobId}/heatmap`);
          if (hres.ok && hres.status !== 204) setHeatmap(await hres.json());
        }
      }
    }, 3000);
  };

  const handleUpload = async () => {
    if (!file) { alert('영상을 선택하세요'); return; }
    setUploading(true); setStats(null); setHeatmap(null);
    const fd = new FormData();
    fd.append('video', file);
    if (recordedAt) fd.append('recordedAt', recordedAt + ':00+09:00');
    try {
      const res = await fetch(`${API}/jobs`, { method: 'POST', body: fd });
      const data = await res.json();
      setJob(data);
      startPolling(data.id);
    } catch (e) {
      alert('업로드 실패: ' + e.message);
    } finally {
      setUploading(false);
    }
  };

  useEffect(() => () => { if (pollRef.current) clearInterval(pollRef.current); }, []);

  const purchaseData = stats ? [
    { name: '구매 추정', value: stats.visitor_count - stats.no_purchase_count },
    { name: '미구매 추정', value: stats.no_purchase_count },
  ] : [];
  const ageData = stats ? Object.entries(stats.age_distribution).map(([k, v]) => ({ name: k, count: v })) : [];

  return (
    <div style={{ maxWidth: 720, margin: '40px auto', fontFamily: 'sans-serif' }}>
      <h1>🛒 RetailLens</h1>
      <p style={{ color: '#666' }}>매장 영상 업로드 → 고객 행동 분석</p>

      <div style={{ border: '1px solid #ddd', borderRadius: 8, padding: 24 }}>
        <input type="file" accept="video/mp4" onChange={e => setFile(e.target.files[0])} />
        <div style={{ marginTop: 12 }}>
          <label>촬영 시각: </label>
          <input type="datetime-local" value={recordedAt}
                 onChange={e => setRecordedAt(e.target.value)} />
        </div>
        <button onClick={handleUpload} disabled={uploading}
                style={{ marginTop: 16, padding: '10px 20px', cursor: 'pointer' }}>
          {uploading ? '업로드 중...' : '분석 시작'}
        </button>
      </div>

      {job && job.status === 'RUNNING' && <p style={{ marginTop: 20 }}>⏳ 분석 중...</p>}
      {job && job.status === 'FAILED' && <p style={{ marginTop: 20, color: 'red' }}>❌ {job.error_message}</p>}

      {stats && (
        <div style={{ marginTop: 24 }}>
          <h2>분석 결과</h2>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12 }}>
            <Card label="총 방문자" value={stats.visitor_count} />
            <Card label="평균 체류시간" value={`${stats.avg_dwell_sec}s`} />
            <Card label="추정 전환율" value={`${(stats.estimated_conversion_rate * 100).toFixed(1)}%`} />
            <Card label="미구매 추정" value={stats.no_purchase_count} />
            <Card label="관심구역 방문" value={stats.checkout_visit_count} />
            <Card label="평균 관심구역 체류" value={`${stats.avg_checkout_dwell_sec}s`} />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginTop: 24 }}>
            <div>
              <h3>구매 추정 비율</h3>
              <ResponsiveContainer width="100%" height={220}>
                <PieChart>
                  <Pie data={purchaseData} dataKey="value" nameKey="name" outerRadius={70} label>
                    {purchaseData.map((_, i) => <Cell key={i} fill={COLORS[i]} />)}
                  </Pie>
                  <Tooltip /><Legend />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div>
              <h3>연령대 분포</h3>
              <ResponsiveContainer width="100%" height={220}>
                <BarChart data={ageData}>
                  <XAxis dataKey="name" /><YAxis allowDecimals={false} /><Tooltip />
                  <Bar dataKey="count" fill="#2196f3" />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>

          <div style={{ marginTop: 24 }}><Heatmap data={heatmap} /></div>
        </div>
      )}
    </div>
  );
}