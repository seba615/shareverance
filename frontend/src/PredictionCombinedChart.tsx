import { formatMinor } from './analyticsModel';
import { formatPredictionMonth } from './predictionModel';

export interface CombinedChartPoint {
  month: string;
  amountMinor: number | null;
  type: 'history' | 'forecast';
  horizon?: number;
}

interface Props {
  points: CombinedChartPoint[];
  caption?: string;
}

export function PredictionCombinedChart({ points, caption = 'Evolución histórica y proyección multimes' }: Props) {
  if (points.length === 0) return null;

  const validAmounts = points
    .map(p => p.amountMinor)
    .filter((a): a is number => a !== null && a > 0);

  const maxAmount = Math.max(1, ...validAmounts);
  const chartHeight = 220;
  const paddingBottom = 40;
  const paddingTop = 20;
  const availableHeight = chartHeight - paddingBottom - paddingTop;
  const colWidth = 64;
  const chartWidth = Math.max(380, points.length * colWidth + 20);

  return (
    <div className="analytics-chart-scroll" tabIndex={0} role="region" aria-label="Gráfico combinado de gastos históricos y proyección multimes">
      <svg
        className="analytics-chart"
        style={{ minWidth: chartWidth }}
        viewBox={`0 0 ${chartWidth} ${chartHeight}`}
        role="img"
        aria-label={`${caption}; los valores detallados se encuentran en las tablas siguientes`}
      >
        {/* Línea base */}
        <line
          x1={10}
          y1={chartHeight - paddingBottom}
          x2={chartWidth - 10}
          y2={chartHeight - paddingBottom}
          stroke="#cbd5e1"
          strokeWidth={1}
        />

        {points.map((pt, index) => {
          const x = index * colWidth + 16;
          const isHistory = pt.type === 'history';
          const hasValue = pt.amountMinor !== null && pt.amountMinor > 0;
          const barHeight = hasValue ? Math.max(2, (pt.amountMinor! / maxAmount) * availableHeight) : 0;
          const y = chartHeight - paddingBottom - barHeight;

          // Colores accesibles: azul para histórico, verde/turquesa con borde punteado para proyección
          const barFill = isHistory ? '#2563eb' : '#0d9488';
          const strokeColor = isHistory ? '#1d4ed8' : '#0f766e';

          return (
            <g key={pt.month}>
              <title>
                {formatPredictionMonth(pt.month)} ({isHistory ? 'Histórico confirmado' : `Proyección h=${pt.horizon ?? index + 1}`}):{' '}
                {hasValue ? formatMinor(pt.amountMinor) : 'Sin datos'}
              </title>

              {hasValue ? (
                <rect
                  x={x}
                  y={y}
                  width={34}
                  height={barHeight}
                  fill={barFill}
                  stroke={strokeColor}
                  strokeWidth={isHistory ? 1 : 2}
                  strokeDasharray={isHistory ? 'none' : '3 2'}
                  rx={3}
                />
              ) : (
                <rect
                  x={x}
                  y={chartHeight - paddingBottom - 2}
                  width={34}
                  height={2}
                  fill="#94a3b8"
                  rx={1}
                />
              )}

              {/* Indicador de tipo si es proyección */}
              {!isHistory && (
                <text
                  x={x + 17}
                  y={Math.max(14, y - 6)}
                  textAnchor="middle"
                  fill="#0f766e"
                  fontSize="10"
                  fontWeight="600"
                >
                  h={pt.horizon}
                </text>
              )}

              {/* Etiqueta del mes */}
              <text
                x={x + 17}
                y={chartHeight - paddingBottom + 18}
                textAnchor="middle"
                fill="#334155"
                fontSize="11"
              >
                {pt.month.slice(2)}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}
