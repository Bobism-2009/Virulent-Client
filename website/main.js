(() => {
  const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (reduce) return;

  const orbs = document.querySelectorAll(".orb");
  const spore = document.querySelector(".spore");

  let mx = 0;
  let my = 0;
  let cx = 0;
  let cy = 0;

  window.addEventListener(
    "pointermove",
    (e) => {
      const x = (e.clientX / window.innerWidth - 0.5) * 2;
      const y = (e.clientY / window.innerHeight - 0.5) * 2;
      mx = x;
      my = y;
    },
    { passive: true }
  );

  const tick = () => {
    cx += (mx - cx) * 0.06;
    cy += (my - cy) * 0.06;

    orbs.forEach((orb, i) => {
      const strength = i === 0 ? 18 : -14;
      orb.style.translate = `${cx * strength}px ${cy * strength}px`;
    });

    if (spore) {
      spore.style.transform = `translate(${cx * -12}px, ${cy * -8}px) scale(1.04)`;
    }

    requestAnimationFrame(tick);
  };

  requestAnimationFrame(tick);
})();
