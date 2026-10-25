import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

const server = await createServer({ configFile: false, server: { middlewareMode: true, watch: null }, appType: 'custom' });
after(() => server.close());

const { PredictionCombinedChart } = await server.ssrLoadModule('/src/PredictionCombinedChart.tsx');
const render = (component, props) => renderToStaticMarkup(createElement(component, props));

test('gráfico combinado no renderiza nada si la lista de puntos está vacía', () => {
  const markup = render(PredictionCombinedChart, { points: [] });
  assert.equal(markup, '');
});

test('gráfico combinado renderiza barras históricas y barras de proyección con atributos accesibles', () => {
  const points = [
    { month: '2024-11', amountMinor: 15000000, type: 'history' },
    { month: '2024-12', amountMinor: 16000000, type: 'history' },
    { month: '2025-01', amountMinor: 17000000, type: 'forecast', horizon: 1 },
    { month: '2025-02', amountMinor: 18000000, type: 'forecast', horizon: 2 },
    { month: '2025-03', amountMinor: 19000000, type: 'forecast', horizon: 3 },
    { month: '2025-04', amountMinor: 20000000, type: 'forecast', horizon: 4 },
  ];

  const markup = render(PredictionCombinedChart, { points, caption: 'Proyección 4 meses' });
  assert.match(markup, /role="img"/);
  assert.match(markup, /aria-label="Proyección 4 meses/);
  assert.match(markup, /h=1/);
  assert.match(markup, /h=4/);
  assert.match(markup, /24-11/);
  assert.match(markup, /25-04/);
});
