import { useEffect, useRef } from "react";
import {
  AmbientLight, Color, DirectionalLight, Group, Mesh, MeshPhysicalMaterial, PerspectiveCamera, PointLight, Scene,
  SphereGeometry, TorusGeometry, WebGLRenderer,
} from "three";

/**
 * Lightweight WebGL scene for the dashboard hero: a few glossy spheres and a ring in the brand palette.
 * - Device pixel ratio capped at 1.5 and antialiasing off on high-DPI screens to save GPU time.
 * - The loop runs only while visible on screen and the tab is visible.
 * - With reduced motion, a single static frame is rendered.
 * Calls onFail() when WebGL is unavailable so the caller can show the CSS fallback.
 */
export default function HeroScene({ reduced, onFail }) {
  const host = useRef(null);

  useEffect(() => {
    const el = host.current;
    let renderer;
    try {
      renderer = new WebGLRenderer({ antialias: window.devicePixelRatio < 2, alpha: true, powerPreference: "low-power" });
    } catch {
      onFail?.();
      return undefined;
    }
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5));
    el.appendChild(renderer.domElement);

    const scene = new Scene();
    const camera = new PerspectiveCamera(35, 1, 0.1, 100);
    camera.position.set(0, 0, 9);

    scene.add(new AmbientLight(0xffffff, 0.55));
    const key = new DirectionalLight(0xffffff, 1.6);
    key.position.set(-4, 5, 6);
    scene.add(key);
    const rim = new PointLight(new Color("#22d3a6"), 30, 20);
    rim.position.set(4, -2, 3);
    scene.add(rim);

    const group = new Group();
    scene.add(group);
    const geo = new SphereGeometry(1, 48, 48);
    const material = (hex, rough = 0.25) =>
      new MeshPhysicalMaterial({ color: new Color(hex), roughness: rough, metalness: 0.05, clearcoat: 0.8, clearcoatRoughness: 0.2 });
    const specs = [
      { c: "#22d3b4", s: 1.15, p: [1.3, 0.3, 0] },
      { c: "#f7a43c", s: 0.75, p: [-1.4, 0.9, -0.6] },
      { c: "#2bc6dd", s: 0.55, p: [-0.4, -1.2, 0.8] },
      { c: "#1f3e63", s: 0.9, p: [-2.2, -0.7, -1.2], rough: 0.4 },
      { c: "#f5c84b", s: 0.32, p: [2.4, 1.5, 0.5] },
    ];
    const meshes = specs.map((sp) => {
      const m = new Mesh(geo, material(sp.c, sp.rough));
      m.scale.setScalar(sp.s);
      m.position.set(...sp.p);
      m.userData.base = [...sp.p];
      group.add(m);
      return m;
    });
    const ringGeo = new TorusGeometry(1.25, 0.18, 24, 96);
    const ring = new Mesh(ringGeo, material("#16324a", 0.35));
    ring.position.set(0.3, 0.1, -1.6);
    ring.rotation.set(1.1, 0.3, 0);
    group.add(ring);

    const resize = () => {
      const w = el.clientWidth || 1;
      const h = el.clientHeight || 1;
      renderer.setSize(w, h, false);
      camera.aspect = w / h;
      camera.updateProjectionMatrix();
    };
    resize();
    const ro = new ResizeObserver(resize);
    ro.observe(el);

    let raf = 0;
    let visible = true;
    const start = performance.now();
    const frame = (now) => {
      const t = (now - start) / 1000;
      meshes.forEach((m, i) => {
        const [x, y, z] = m.userData.base;
        m.position.set(x + Math.sin(t * 0.4 + i) * 0.12, y + Math.cos(t * 0.5 + i * 1.3) * 0.15, z);
      });
      group.rotation.y = Math.sin(t * 0.15) * 0.25;
      ring.rotation.z = t * 0.2;
      renderer.render(scene, camera);
      if (visible && !reduced && !document.hidden) raf = requestAnimationFrame(frame);
    };
    const kick = () => { cancelAnimationFrame(raf); raf = requestAnimationFrame(frame); };
    if (reduced) renderer.render(scene, camera);
    else kick();

    const io = new IntersectionObserver(([entry]) => { visible = entry.isIntersecting; if (visible && !reduced) kick(); });
    io.observe(el);
    const onVis = () => { if (!document.hidden && visible && !reduced) kick(); };
    document.addEventListener("visibilitychange", onVis);

    return () => {
      cancelAnimationFrame(raf);
      io.disconnect();
      ro.disconnect();
      document.removeEventListener("visibilitychange", onVis);
      geo.dispose();
      ringGeo.dispose();
      [...meshes, ring].forEach((m) => m.material.dispose());
      renderer.dispose();
      renderer.domElement.remove();
    };
  }, [reduced, onFail]);

  return <div ref={host} className="hero-scene" aria-hidden="true" />;
}
