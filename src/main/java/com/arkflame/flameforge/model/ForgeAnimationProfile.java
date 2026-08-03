package com.arkflame.flameforge.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ForgeAnimationProfile {
    private final int durationTicks;
    private final int intervalTicks;
    private final ChargeSound chargeSound;
    private final ChargeParticle chargeParticle;
    private final ImpactParticle impactParticle;
    private final OutcomeFeedback successFeedback;
    private final OutcomeFeedback breakFeedback;
    private final OutcomeFeedback curseFeedback;
    private final List<AnimationStep> steps;

    public ForgeAnimationProfile(int durationTicks, int intervalTicks,
                                  ChargeSound chargeSound, ChargeParticle chargeParticle,
                                  ImpactParticle impactParticle, OutcomeFeedback successFeedback,
                                  OutcomeFeedback breakFeedback, OutcomeFeedback curseFeedback) {
        this.durationTicks = durationTicks;
        this.intervalTicks = intervalTicks;
        this.chargeSound = chargeSound;
        this.chargeParticle = chargeParticle;
        this.impactParticle = impactParticle;
        this.successFeedback = successFeedback;
        this.breakFeedback = breakFeedback;
        this.curseFeedback = curseFeedback;
        this.steps = Collections.emptyList();
    }

    public ForgeAnimationProfile(List<AnimationStep> steps) {
        this.durationTicks = 20;
        this.intervalTicks = 4;
        this.chargeSound = null;
        this.chargeParticle = null;
        this.impactParticle = null;
        this.successFeedback = null;
        this.breakFeedback = null;
        this.curseFeedback = null;
        this.steps = steps != null ? Collections.unmodifiableList(new java.util.ArrayList<>(steps)) : Collections.emptyList();
    }

    public List<AnimationStep> getSteps() { return steps; }
    public int getDurationTicks() { return durationTicks; }
    public int getIntervalTicks() { return intervalTicks; }
    public ChargeSound getChargeSound() { return chargeSound; }
    public ChargeParticle getChargeParticle() { return chargeParticle; }
    public ImpactParticle getImpactParticle() { return impactParticle; }
    public OutcomeFeedback getSuccessFeedback() { return successFeedback; }
    public OutcomeFeedback getBreakFeedback() { return breakFeedback; }
    public OutcomeFeedback getCurseFeedback() { return curseFeedback; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ForgeAnimationProfile)) return false;
        ForgeAnimationProfile that = (ForgeAnimationProfile) o;
        return Objects.equals(steps, that.steps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(steps);
    }

    @Override
    public String toString() {
        return "ForgeAnimationProfile{steps=" + steps + "}";
    }

    public static final class ChargeSound {
        private final List<String> candidates;
        private final BigDecimal volume;
        private final BigDecimal startPitch;
        private final BigDecimal endPitch;

        public ChargeSound(List<String> candidates, BigDecimal volume, BigDecimal startPitch, BigDecimal endPitch) {
            this.candidates = candidates != null ? Collections.unmodifiableList(new java.util.ArrayList<>(candidates)) : Collections.emptyList();
            this.volume = volume != null ? volume : BigDecimal.ONE;
            this.startPitch = startPitch != null ? startPitch : new BigDecimal("0.50");
            this.endPitch = endPitch != null ? endPitch : new BigDecimal("2.00");
        }

        public List<String> getCandidates() { return candidates; }
        public BigDecimal getVolume() { return volume; }
        public BigDecimal getStartPitch() { return startPitch; }
        public BigDecimal getEndPitch() { return endPitch; }
    }

    public static final class ChargeParticle {
        private final List<String> candidates;
        private final int count;
        private final BigDecimal radius;

        public ChargeParticle(List<String> candidates, int count, BigDecimal radius) {
            this.candidates = candidates != null ? Collections.unmodifiableList(new java.util.ArrayList<>(candidates)) : Collections.emptyList();
            this.count = count;
            this.radius = radius != null ? radius : new BigDecimal("1.20");
        }

        public List<String> getCandidates() { return candidates; }
        public int getCount() { return count; }
        public BigDecimal getRadius() { return radius; }
    }

    public static final class ImpactParticle {
        private final List<String> candidates;
        private final int count;
        private final BigDecimal radius;

        public ImpactParticle(List<String> candidates, int count, BigDecimal radius) {
            this.candidates = candidates != null ? Collections.unmodifiableList(new java.util.ArrayList<>(candidates)) : Collections.emptyList();
            this.count = count;
            this.radius = radius != null ? radius : BigDecimal.ONE;
        }

        public List<String> getCandidates() { return candidates; }
        public int getCount() { return count; }
        public BigDecimal getRadius() { return radius; }
    }

    public static final class OutcomeFeedback {
        private final List<String> soundCandidates;
        private final List<String> particleCandidates;
        private final String title;
        private final String actionBar;

        public OutcomeFeedback(List<String> soundCandidates, List<String> particleCandidates, String title) {
            this(soundCandidates, particleCandidates, title, null);
        }

        public OutcomeFeedback(List<String> soundCandidates, List<String> particleCandidates, String title, String actionBar) {
            this.soundCandidates = soundCandidates != null ? Collections.unmodifiableList(new java.util.ArrayList<>(soundCandidates)) : Collections.emptyList();
            this.particleCandidates = particleCandidates != null ? Collections.unmodifiableList(new java.util.ArrayList<>(particleCandidates)) : Collections.emptyList();
            this.title = title;
            this.actionBar = actionBar;
        }

        public List<String> getSoundCandidates() { return soundCandidates; }
        public List<String> getParticleCandidates() { return particleCandidates; }
        public String getTitle() { return title; }
        public String getActionBar() { return actionBar; }
        public String getSound() { return soundCandidates != null && !soundCandidates.isEmpty() ? soundCandidates.get(0) : null; }
    }

    public static final class AnimationStep {
        private final int ticks;
        private final String sound;
        private final String particle;
        private final String title;

        public AnimationStep(int ticks, String sound, String particle, String title) {
            this.ticks = ticks;
            this.sound = sound;
            this.particle = particle;
            this.title = title;
        }

        public int getTicks() { return ticks; }
        public String getSound() { return sound; }
        public String getParticle() { return particle; }
        public String getTitle() { return title; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AnimationStep)) return false;
            AnimationStep that = (AnimationStep) o;
            return ticks == that.ticks &&
                   Objects.equals(sound, that.sound) &&
                   Objects.equals(particle, that.particle) &&
                   Objects.equals(title, that.title);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ticks, sound, particle, title);
        }

        @Override
        public String toString() {
            return "AnimationStep{ticks=" + ticks + ", sound=" + sound +
                   ", particle=" + particle + ", title=" + title + "}";
        }
    }
}
