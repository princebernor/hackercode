import { useEffect, useRef } from "react";

/**
 * Layered CSS 3D spheres (radial-gradient "materials"). Cheap to render, no WebGL required.
 * With `parallax`, the layer tilts gently toward the pointer; disabled when reduced motion is preferred.
 */
const DEFAULT_ORBS = [
  { c: "orange", s: 300, x: -12, y: -14, z: -60, dur: 18, dx: 14, dy: 10 },
  { c: "yellow", s: 150, x: 20, y: 6, z: -30, dur: 16, dx: -8, dy: 14 },
  { c: "navy", s: 260, x: 62, y: -10, z: -40, dur: 20, dx: -12, dy: 12, ring: true },
  { c: "teal", s: 210, x: 74, y: 22, z: 10, dur: 14, dx: 10, dy: -14 },
  { c: "cyan", s: 170, x: -6, y: 50, z: -20, dur: 17, dx: 12, dy: -10 },
  { c: "teal", s: 120, x: 30, y: 82, z: 30, dur: 13, dx: -10, dy: -8 },
  { c: "navy", s: 220, x: 70, y: 70, z: -10, dur: 19, dx: 8, dy: 10 },
  { c: "orange", s: 90, x: 86, y: 48, z: 40, dur: 12, dx: -6, dy: 12 },
];

export default function Orbs({ orbs = DEFAULT_ORBS, parallax = true, reduced = false }) {
  const ref = useRef(null);
  useEffect(() => {
    if (!parallax || reduced) return;
    const node = ref.current;
    const host = node?.parentElement;
    if (!host) return;
    let frame = 0;
    const onMove = (e) => {
      const r = host.getBoundingClientRect();
      const px = (e.clientX - r.left) / r.width - 0.5;
      const py = (e.clientY - r.top) / r.height - 0.5;
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        node.style.transform = `rotateY(${px * 6}deg) rotateX(${-py * 6}deg) translate3d(${px * -14}px, ${py * -14}px, 0)`;
      });
    };
    const reset = () => { node.style.transform = ""; };
    host.addEventListener("pointermove", onMove);
    host.addEventListener("pointerleave", reset);
    return () => { host.removeEventListener("pointermove", onMove); host.removeEventListener("pointerleave", reset); cancelAnimationFrame(frame); };
  }, [parallax, reduced]);

  return (
    <div className="orbs" ref={ref} aria-hidden="true">
      {orbs.map((o, i) => (
        <span
          key={i}
          className={`orb ${o.c}${o.ring ? " ring" : ""}`}
          style={{
            width: o.s, height: o.s, left: `${o.x}%`, top: `${o.y}%`,
            "--z": `${o.z}px`, "--dx": `${o.dx}px`, "--dy": `${o.dy}px`, "--orb-dur": `${o.dur}s`,
            animationDelay: `${-i * 1.7}s`,
            animationPlayState: reduced ? "paused" : undefined,
          }}
        />
      ))}
    </div>
  );
}
