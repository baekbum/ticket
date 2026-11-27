import { useEffect, useRef, useState, type FormEvent } from 'react';
import { adminFetch } from './api';
import { detectionLabels, detectionLogEndpoint, type LogDetail, type LogPage } from './ticketingDetection';

type TestRun = { userId: string; sessionKeyHash: string; types: string[] };

const publishUrl = '/ticket/api/v1/manage/test/ticketing-detection-log/publish';
const verificationTimeout = 30_000;
const pollInterval = 750;

export default function TicketingDetectionTest({ onOpenLogs }: { onOpenLogs: (userId: string, sessionKeyHash: string) => void }) {
  const activeRequest = useRef<AbortController | null>(null);
  const [run, setRun] = useState<TestRun | null>(null);
  const [logs, setLogs] = useState<LogDetail[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [userId, setUserId] = useState('');
  const [detectionType, setDetectionType] = useState('');

  useEffect(() => () => activeRequest.current?.abort(), []);

  async function runTest(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const testUserId = userId.trim();
    if (!testUserId || activeRequest.current) return;
    const controller = new AbortController();
    activeRequest.current = controller;
    const timeout = AbortSignal.timeout(verificationTimeout);
    const signal = AbortSignal.any([controller.signal, timeout]);
    setBusy(true);
    setError('');
    setRun(null);
    setLogs([]);

    try {
      const response = await adminFetch(publishUrl, {
        method: 'POST', signal, headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: testUserId, detectionType: detectionType || null }),
      });
      if (!response.ok) throw new Error(`테스트 이벤트 발행을 요청하지 못했습니다. (HTTP ${response.status})`);
      const testRun: TestRun = await response.json();
      signal.throwIfAborted();
      setRun(testRun);

      for (;;) {
        const response = await adminFetch(detectionLogEndpoint, {
          method: 'POST', signal, headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ userId: testRun.userId, sessionKeyHash: testRun.sessionKeyHash, page: 0, size: testRun.types.length }),
        });
        if (!response.ok) throw new Error(`감사 로그를 조회하지 못했습니다. (HTTP ${response.status})`);
        const page: LogPage = await response.json();
        const logIds = new Map(page.content.map(log => [log.detectionType, log.id]));
        if (testRun.types.every(type => logIds.has(type))) {
          const details = await Promise.all(testRun.types.map(async type => {
            const result = await adminFetch(`${detectionLogEndpoint}/id/${logIds.get(type)}`, { signal });
            if (!result.ok) throw new Error(`탐지 근거를 조회하지 못했습니다. (HTTP ${result.status})`);
            const log: LogDetail = await result.json();
            if (log.userId !== testRun.userId || log.sessionKeyHash !== testRun.sessionKeyHash || log.detectionType !== type || log.evidence.test !== true) {
              throw new Error('저장된 로그가 요청한 테스트 이벤트와 일치하지 않습니다.');
            }
            return log;
          }));
          signal.throwIfAborted();
          setLogs(details);
          return;
        }
        await new Promise(resolve => window.setTimeout(resolve, pollInterval));
        signal.throwIfAborted();
      }
    } catch (error) {
      if (!controller.signal.aborted) {
        setError(timeout.aborted ? '30초 안에 모든 테스트 로그를 확인하지 못했습니다.'
          : error instanceof Error && !(error instanceof TypeError) ? error.message : '테스트 요청에 실패했습니다.');
      }
    } finally {
      if (activeRequest.current === controller) {
        activeRequest.current = null;
        if (!controller.signal.aborted) setBusy(false);
      }
    }
  }

  return <section className="detection-logs detection-test">
    <form className="detection-filters" onSubmit={runTest}>
      <label>사용자 ID<input required disabled={busy} maxLength={50} autoComplete="off" value={userId}
        onChange={event => setUserId(event.target.value)} /></label>
      <label>탐지 유형<select disabled={busy} value={detectionType} onChange={event => setDetectionType(event.target.value)}>
        <option value="">전체 유형</option>
        {Object.entries(detectionLabels).map(([type, label]) => <option key={type} value={type}>{label}</option>)}
      </select></label>
      <div className="detection-filter-actions">
        {run && <button type="button" onClick={() => onOpenLogs(run.userId, run.sessionKeyHash)}>모니터링에서 보기</button>}
        <button type="submit" className="detection-primary" disabled={busy || !userId.trim()}>샘플 이벤트 발행</button>
      </div>
    </form>
    {run && <p>테스트 사용자 ID <code>{run.userId}</code></p>}
    {busy && <p role="status">{run ? '감사 로그 저장을 확인하고 있습니다.' : '테스트 이벤트 발행을 요청하고 있습니다.'}</p>}
    {error && <p role="alert" className="detection-error">{error}</p>}
    {logs.length > 0 && <>
      <p role="status">테스트 로그 {logs.length}건의 저장과 상세 조회를 확인했습니다.</p>
      <div className="detection-table-wrap">
        <table className="detection-table" aria-label="저장된 테스트 로그">
          <thead><tr><th scope="col">로그 ID</th><th scope="col">탐지 유형</th><th scope="col">탐지 근거</th></tr></thead>
          <tbody>{logs.map(log => <tr key={log.id}>
            <td>{log.id}</td><td>{detectionLabels[log.detectionType] ?? log.detectionType}</td>
            <td><code>{JSON.stringify(log.evidence)}</code></td>
          </tr>)}</tbody>
        </table>
      </div>
    </>}
  </section>;
}
