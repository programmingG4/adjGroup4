document.addEventListener("DOMContentLoaded", () => {
    const slider = document.querySelector("[data-home-news-slider]");
    if (!slider) {
        return;
    }

    const track = slider.querySelector("[data-home-news-track]");
    const dots = Array.from(slider.querySelectorAll("[data-slide-index]"));
    const originalSlides = Array.from(track?.querySelectorAll(".home-news-slide") || []);
    const slideCount = originalSlides.length;
    const SLIDE_DELAY = 10000;

    if (!track || slideCount <= 1) {
        dots.forEach((dot, index) => dot.classList.toggle("is-active", index === 0));
        return;
    }

    const firstClone = originalSlides[0].cloneNode(true);
    firstClone.setAttribute("aria-hidden", "true");
    firstClone.tabIndex = -1;
    track.appendChild(firstClone);

    let currentIndex = 0;
    let timerId = null;
    let isResetting = false;

    function setActiveDot(index) {
        const visibleIndex = index >= slideCount ? 0 : index;
        dots.forEach((dot, dotIndex) => {
            dot.classList.toggle("is-active", dotIndex === visibleIndex);
            dot.setAttribute("aria-current", dotIndex === visibleIndex ? "true" : "false");
        });
    }

    function moveTo(index, withTransition = true) {
        currentIndex = index;
        track.style.transition = withTransition ? "" : "none";
        track.style.transform = `translateX(-${index * 100}%)`;
        setActiveDot(index);
    }

    function restartTimer() {
        window.clearInterval(timerId);
        timerId = window.setInterval(() => {
            moveTo(currentIndex + 1, true);
        }, SLIDE_DELAY);
    }

    track.addEventListener("transitionend", () => {
        if (currentIndex !== slideCount || isResetting) {
            return;
        }

        isResetting = true;
        moveTo(0, false);
        track.offsetHeight;
        window.requestAnimationFrame(() => {
            track.style.transition = "";
            isResetting = false;
        });
    });

    dots.forEach((dot) => {
        dot.addEventListener("click", () => {
            const index = Number.parseInt(dot.dataset.slideIndex || "0", 10);
            moveTo(Number.isNaN(index) ? 0 : index, true);
            restartTimer();
        });
    });

    slider.addEventListener("mouseenter", () => window.clearInterval(timerId));
    slider.addEventListener("mouseleave", restartTimer);

    moveTo(0, false);
    restartTimer();
});
