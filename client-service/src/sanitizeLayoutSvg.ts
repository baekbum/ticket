import createDOMPurify from 'dompurify';

const purifier = createDOMPurify(window);
const localReference = /^url\(#[A-Za-z_][A-Za-z0-9_.-]*\)$/;
const presentationValue = /^[A-Za-z0-9#.,%+\-\s]+$|^(?:rgb|rgba|hsl|hsla)\([0-9.,%+\-\s]+\)$/;

purifier.addHook('uponSanitizeAttribute', (_node, attribute) => {
  if (['fill', 'stroke', 'clip-path'].includes(attribute.attrName)) {
    const value = attribute.attrValue.trim();
    attribute.keepAttr = presentationValue.test(value) || localReference.test(value);
  }
});

/** Keep only static layout markup, including the attributes used by React's area selection handlers. */
export function sanitizeLayoutSvg(svg: string): string {
  return purifier.sanitize(svg, {
    ALLOWED_TAGS: [
      'svg', 'g', 'path', 'rect', 'circle', 'ellipse', 'line', 'polyline', 'polygon',
      'text', 'tspan', 'title', 'desc', 'defs', 'linearGradient', 'radialGradient', 'stop', 'clipPath',
    ],
    ALLOWED_ATTR: [
      'xmlns', 'id', 'class', 'viewBox', 'width', 'height', 'preserveAspectRatio', 'transform',
      'x', 'y', 'x1', 'y1', 'x2', 'y2', 'cx', 'cy', 'r', 'rx', 'ry', 'd', 'points',
      'dx', 'dy', 'rotate', 'textLength', 'lengthAdjust', 'fill', 'stroke', 'stroke-width',
      'fill-opacity', 'stroke-opacity', 'opacity', 'fill-rule', 'clip-rule', 'clip-path',
      'stroke-linecap', 'stroke-linejoin', 'stroke-miterlimit', 'stroke-dasharray', 'stroke-dashoffset',
      'font-size', 'font-family', 'font-weight', 'font-style', 'text-anchor', 'dominant-baseline',
      'letter-spacing', 'word-spacing', 'visibility', 'display', 'vector-effect',
      'gradientUnits', 'gradientTransform', 'spreadMethod', 'fx', 'fy', 'fr', 'offset',
      'stop-color', 'stop-opacity', 'clipPathUnits', 'data-layout-key', 'data-area-name',
      'data-grade', 'data-price', 'data-area-id', 'tabindex', 'role', 'aria-label',
    ],
    ALLOW_DATA_ATTR: false,
    ALLOW_ARIA_ATTR: false,
    KEEP_CONTENT: false,
  });
}
