export const defaultRefreshSeconds = 30;

const refreshIntervals = [
  { seconds: 5, label: '5초' },
  { seconds: 10, label: '10초' },
  { seconds: defaultRefreshSeconds, label: '30초' },
  { seconds: 60, label: '1분' },
  { seconds: 300, label: '5분' },
  { seconds: 600, label: '10분' },
];

export default function AutoRefreshControls({ enabled, seconds, onToggle, onIntervalChange }: {
  enabled: boolean;
  seconds: number;
  onToggle: () => void;
  onIntervalChange: (seconds: number) => void;
}) {
  return <div className="operations-refresh">
    <button type="button" className="operations-refresh-toggle" role="switch" aria-checked={enabled} onClick={onToggle}>
      <i className="ti ti-refresh operations-refresh-icon" aria-hidden="true" />
      <span className="operations-refresh-track" aria-hidden="true" />
      자동 새로고침
    </button>
    <label className="operations-refresh-period">
      갱신 주기
      <select value={seconds} disabled={!enabled} onChange={event => onIntervalChange(Number(event.target.value))}>
        {refreshIntervals.map(({ seconds: value, label }) => <option key={value} value={value}>{label}</option>)}
      </select>
    </label>
  </div>;
}
