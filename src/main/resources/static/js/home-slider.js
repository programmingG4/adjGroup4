document.addEventListener("DOMContentLoaded", () => {
    const slider = document.querySelector("[data-home-news-slider]");
    if (!slider) {
        return;
    }

    const track = slider.querySelector("[data-home-news-track]");
    const dots = Array.from(slider.querySelectorAll("[data-slide-index]"));
    const originalSlides = Array.from(track?.querySelectorAll(".home-news-slide") || []);
    const slideCount = originalSlides.length;
    const SLIDE_DELAY = 8000;

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
    let isAnimating = false;
    let pendingResetToFirst = false;

    function getVisibleIndex(index) {
        return index >= slideCount ? 0 : index;
    }

    function setActiveDot(index) {
        const visibleIndex = getVisibleIndex(index);
        dots.forEach((dot, dotIndex) => {
            dot.classList.toggle("is-active", dotIndex === visibleIndex);
            dot.setAttribute("aria-current", dotIndex === visibleIndex ? "true" : "false");
        });
    }

    function setTrackPosition(index, withTransition = true) {
        track.style.transition = withTransition ? "" : "none";
        track.style.transform = `translateX(-${index * 100}%)`;
    }

    function moveTo(index, withTransition = true) {
        if (isAnimating && withTransition) {
            return;
        }

        currentIndex = index;
        isAnimating = withTransition;
        setTrackPosition(index, withTransition);
        setActiveDot(index);
    }

    function jumpToRealFirst() {
        currentIndex = 0;
        pendingResetToFirst = false;
        isAnimating = false;
        setTrackPosition(0, false);

        // Force the browser to apply the non-transition jump before restoring CSS transition.
        track.offsetHeight;
        window.requestAnimationFrame(() => {
            track.style.transition = "";
        });
    }

    function moveNext() {
        moveTo(currentIndex + 1, true);
    }

    function moveToDotIndex(targetIndex) {
        const safeTargetIndex = Number.isNaN(targetIndex) ? 0 : targetIndex;
        const visibleIndex = getVisibleIndex(currentIndex);

        // From the last real slide to the first slide, keep the same forward direction by
        // moving to the cloned first slide, then silently reset to the real first slide.
        if (visibleIndex === slideCount - 1 && safeTargetIndex === 0) {
            pendingResetToFirst = true;
            moveTo(slideCount, true);
            return;
        }

        moveTo(safeTargetIndex, true);
    }

    function restartTimer() {
        window.clearInterval(timerId);
        timerId = window.setInterval(moveNext, SLIDE_DELAY);
    }

    track.addEventListener("transitionend", (event) => {
        if (event.target !== track || event.propertyName !== "transform") {
            return;
        }

        isAnimating = false;

        if (currentIndex === slideCount || pendingResetToFirst) {
            jumpToRealFirst();
        }
    });

    dots.forEach((dot) => {
        dot.addEventListener("click", () => {
            const index = Number.parseInt(dot.dataset.slideIndex || "0", 10);
            moveToDotIndex(index);
            restartTimer();
        });
    });

    slider.addEventListener("mouseenter", () => window.clearInterval(timerId));
    slider.addEventListener("mouseleave", restartTimer);

    setTrackPosition(0, false);
    setActiveDot(0);
    restartTimer();
});
