package com.zhanghao.zoomlayout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.Scroller;

public class ZoomLayout extends FrameLayout implements ScaleGestureDetector.OnScaleGestureListener {
    private static final String TAG = "ZoomLayout";
    private static final float MIN_SCALE_FACTOR = 0.5f;
    private static final float MAX_SCALE_FACTOR = 4f;
    private static final float INCREASE_SCALE_FACTOR = 0.5f;
    private static final int SCALE_ANIM_DURATION = 400;
    private static final int FLING_ANIM_DURATION = 500;
    private static final float MAX_FLING_DISTANCE = 100;
    private static final int PER_SECONDS = 1000;
    private static final int INVALID_POINTER = -1;
    private static final int SCROLL_EDGE_LENGTH_DP = 100;
    private static final int VERTICAL = 1 << 1;
    private static final int HORIZONTAL = 1 << 2;
    private static final float VELOCITY_PERCENT = 0.3f;
    private float mScaleFactor = 1;
    private int mScaleTouchSlop;
    private VelocityTracker mVelocityTracker;
    private int mMaxFlingVelocity;
    private int mMinFlingVelocity;
    private int mTouchSlop;
    private float mDownX;
    private float mDownY;
    private boolean mIsScrolling = false;
    private boolean mIsScaling = false;
    private boolean mIsFlinging = false;
    private ScaleGestureDetector mScaleGestureDetector;
    private GestureDetector mGestureDetector;
    private ValueAnimator mAnimTranX;
    private ValueAnimator mAnimTranY;
    private RectF mChildBound;
    private RectF mScrollBound;
    private int mScrollEdgeLength;
    private Scroller mScroller;

    private float mMaxScrollUp;
    private float mMaxScrollDown;
    private float mMaxScrollLeft;
    private float mMaxScrollRight;

    public ZoomLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        ViewConfiguration vc = ViewConfiguration.get(context);
        mScaleTouchSlop = vc.getScaledTouchSlop();
        mTouchSlop = vc.getScaledTouchSlop();
        mScaleGestureDetector = new ScaleGestureDetector(context, this);
        mGestureDetector = new GestureDetector(context, mSimpleOnGestureListener);
        mVelocityTracker = VelocityTracker.obtain();
        mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        mMinFlingVelocity = vc.getScaledMinimumFlingVelocity();
        mChildBound = new RectF();
        mScrollBound = new RectF();
        mScrollEdgeLength = DensityUtil.dp2px(context, SCROLL_EDGE_LENGTH_DP);
        mScroller = new Scroller(context);
        Log.d(TAG, "init: mMaxFlingVelocity " + mMaxFlingVelocity);
        Log.d(TAG, "init: mMinFlingVelocity" + mMinFlingVelocity);
    }

    private void checkDirectChildCount() {
        if (getChildCount() > 0) {
            throw new IllegalStateException("ZoomLayout can host only one direct child");
        }
    }

    @Override
    public void addView(View child) {
        checkDirectChildCount();
        super.addView(child);
    }

    @Override
    public void addView(View child, int index) {
        checkDirectChildCount();
        super.addView(child, index);
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        checkDirectChildCount();
        super.addView(child, params);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        checkDirectChildCount();
        super.addView(child, index, params);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            measureChild(getChildAt(i), widthMeasureSpec, heightMeasureSpec);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        View child = child();
        int childMeasureWidth = child.getMeasuredWidth();
        int childMeasureHeight = child.getMeasuredHeight();
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        setChildBound(childMeasureWidth, childMeasureHeight);
        setScrollBound(width, height);
        expandScrollBoundIfNeeded();
        initialMaxScrollParams();
    }

    private void initialMaxScrollParams() {
        View child = child();
        mMaxScrollUp = mScrollBound.top - child.getTop();
        mMaxScrollDown = mScrollBound.bottom - child.getBottom();
        mMaxScrollLeft = mScrollBound.left - child.getLeft();
        mMaxScrollRight = mScrollBound.right - child.getRight();
    }

    private void setChildBound(int childWidth, int childHeight) {
        mChildBound.left = 0;
        mChildBound.top = 0;
        mChildBound.right = mChildBound.left + childWidth;
        mChildBound.bottom = mChildBound.top + childHeight;
    }

    private void setScrollBound(int width, int height) {
        mScrollBound.left = 0;
        mScrollBound.top = 0;
        mScrollBound.right = mChildBound.left + width;
        mScrollBound.bottom = mChildBound.top + height;
    }

    private void expandScrollBoundIfNeeded() {
        if (mChildBound.width() > mScrollBound.width() || mChildBound.height() > mScrollBound.height()) {
            mScrollBound.set(mChildBound);
        }
        mScrollBound.left = mScrollBound.left - mScrollEdgeLength;
        mScrollBound.top = mScrollBound.top - mScrollEdgeLength;
        mScrollBound.right = mScrollBound.right + mScrollEdgeLength;
        mScrollBound.bottom = mScrollBound.bottom + mScrollEdgeLength;
        Log.d(TAG, "expandScrollBoundIfNeeded: " + mScrollBound.toString());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                float x = ev.getX();
                float y = ev.getY();
                mDownX = x;
                mDownY = y;
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                float x = ev.getX();
                float y = ev.getY();
                mIsScrolling = interceptScrollEventIfNeeded(x, y);
                mIsScaling = interceptScaleEventIfNeeded(ev);
                return mIsScrolling || mIsScaling;
            }
        }
        return false;
    }

    private boolean interceptScaleEventIfNeeded(MotionEvent event) {
        int ss = calculateScaleSlop(event);
        return ss >= mScaleTouchSlop;
    }

    private boolean interceptScrollEventIfNeeded(float moveX, float moveY) {
        float deltaX = moveX - mDownX;
        float deltaY = moveY - mDownY;
        return Math.abs(deltaX) > mTouchSlop || Math.abs(deltaY) > mTouchSlop;
    }

    private int calculateScaleSlop(MotionEvent ev) {
        int pCount = ev.getPointerCount();
        if (pCount == 2) {
            int x1 = (int) ev.getX(0);
            int y1 = (int) ev.getY(0);
            int x2 = (int) ev.getX(1);
            int y2 = (int) ev.getY(1);
            return calculateLengthBetween2Point(x1, y1, x2, y2);
        }
        return -1;
    }

    private int calculateLengthBetween2Point(int x1, int y1, int x2, int y2) {
        return (int) Math.sqrt(Math.pow((x1 - x2), 2) + Math.pow((y1 - y2), 2));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mVelocityTracker.addMovement(event);
        mScaleGestureDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                clearAllAnimationsIfNeeded();
                float x = event.getX();
                float y = event.getY();
                mDownX = x;
                mDownY = y;
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                float x = event.getX();
                float y = event.getY();
                float dx = x - mDownX;
                float dy = y - mDownY;
                performChildTranslationIfNeeded(dx, dy);
                mDownX = x;
                mDownY = y;
                break;
            }
            case MotionEvent.ACTION_UP: {
                mVelocityTracker.computeCurrentVelocity(PER_SECONDS, mMaxFlingVelocity);
                float velocityX = mVelocityTracker.getXVelocity();
                float velocityY = mVelocityTracker.getYVelocity();
                Log.d(TAG, "onTouchEvent: velocityX " + velocityX);
                Log.d(TAG, "onTouchEvent: velocityY " + velocityY);
                performChildFlingAnimationIfNeeded(velocityX, velocityY);
                mIsScaling = false;
                mIsScrolling = false;
            }
        }
        mGestureDetector.onTouchEvent(event);
        return true;
    }

    private void clearAllAnimationsIfNeeded() {
        if (mAnimTranX != null && mAnimTranX.isRunning()) {
            mAnimTranX.cancel();
        }
        if (mAnimTranY != null && mAnimTranY.isRunning()) {
            mAnimTranY.cancel();
        }
    }

    private void performChildTranslationIfNeeded(float dx, float dy) {
        if (!mIsScaling && !mIsFlinging) {
            View child = child();
            float originTx = child.getTranslationX();
            float originTy = child.getTranslationY();
            float currentTx = calculateTranslation(child, originTx, dx, HORIZONTAL);
            float currentTy = calculateTranslation(child, originTy, dy, VERTICAL);
            child.setTranslationX(currentTx);
            child.setTranslationY(currentTy);
        }
    }

    private float calculateTranslation(View child, float originTranslation, float deltaTranslation, int direction) {
        float currentTop = child.getY();
        float currentBottom = currentTop + child.getMeasuredHeight();
        float currentLeft = child.getX();
        float currentRight = currentLeft + child.getMeasuredWidth();

        float currentTranslation = originTranslation + deltaTranslation;

        switch (direction) {
            case VERTICAL: {
                if (deltaTranslation < 0) {
                    // scroll up
                    if (currentTop < mScrollBound.top) {
                        return mMaxScrollUp;
                    }
                } else {
                    // scroll down
                    if (currentBottom > mScrollBound.bottom) {
                        return mMaxScrollDown;
                    }
                }
                break;
            }
            case HORIZONTAL: {
                if (deltaTranslation < 0) {
                    //scroll left
                    if (currentLeft < mScrollBound.left) {
                        return mMaxScrollLeft;
                    }
                } else {
                    //scroll right
                    if (currentRight > mScrollBound.right) {
                        return mMaxScrollRight;
                    }
                }
                break;
            }
        }
        return currentTranslation;
    }

    private void performChildFlingAnimationIfNeeded(float velocityX, float velocityY) {
        if (!mIsScaling) {
            View child = child();
            float originTransX = child.getTranslationX();
            float originTransY = child.getTranslationY();
            float dx = velocityX / PER_SECONDS * FLING_ANIM_DURATION;
            float dy = velocityY / PER_SECONDS * FLING_ANIM_DURATION;

            initFlingAnimationIfNeeded(child, originTransX, originTransY, dx, dy);

            AnimatorSet animatorTransXY = new AnimatorSet();
            animatorTransXY.setDuration(FLING_ANIM_DURATION);
            if (mAnimTranX != null && mAnimTranY != null) {
                animatorTransXY.playTogether(mAnimTranX, mAnimTranY);
            } else if (mAnimTranX != null) {
                animatorTransXY.playTogether(mAnimTranX);
            } else if (mAnimTranY != null) {
                animatorTransXY.playTogether(mAnimTranY);
            }
            animatorTransXY.start();
        }

    }

    private void initFlingAnimationIfNeeded(View child, float originTransX, float originTransY, float dx, float dy) {
        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        if (absY > absX) {
            // vertical
            float percent = absX / absY;
            if (percent >= VELOCITY_PERCENT) {
                //init all
                initChildFlingVerticalAnimation(child, originTransY, dy);
                initChildFlingHorizontalAnimation(child, originTransX, dx);
            } else {
                // just vertical
                initChildFlingVerticalAnimation(child, originTransY, dy);
            }
        } else {
            // horizontal
            float percent = absY / absX;
            if (percent >= VELOCITY_PERCENT) {
                //init all
                initChildFlingVerticalAnimation(child, originTransY, dy);
                initChildFlingHorizontalAnimation(child, originTransX, dx);
            } else {
                // just horizontal
                initChildFlingHorizontalAnimation(child, originTransX, dx);
            }
        }

    }

    private void initChildFlingHorizontalAnimation(final View child, final float originTransX, float dx) {
        float currentTransX = originTransX + dx;
        if (currentTransX < mMaxScrollLeft) {
            currentTransX = mMaxScrollLeft;
        } else if (currentTransX > mMaxScrollRight) {
            currentTransX = mMaxScrollRight;
        }
        final float diffX = currentTransX - originTransX;
        mAnimTranX = ValueAnimator.ofFloat(originTransX, currentTransX)
                .setDuration(FLING_ANIM_DURATION);
        mAnimTranX.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = animation.getAnimatedFraction();
                float animateX = fraction * diffX;
                child.setTranslationX(originTransX + animateX);
            }
        });
        mAnimTranX.setInterpolator(new DecelerateInterpolator());
        mAnimTranX.addListener(mFlingAnimatorListener);
    }

    private void initChildFlingVerticalAnimation(final View child, final float originTransY, float dy) {
        float currentTransY = originTransY + dy;
        if (currentTransY <= mMaxScrollUp) {
            currentTransY = mMaxScrollUp;
        } else if (currentTransY > mMaxScrollDown) {
            currentTransY = mMaxScrollDown;
        }
        final float diffY = currentTransY - originTransY;
        mAnimTranY = ValueAnimator.ofFloat(originTransY, currentTransY)
                .setDuration(FLING_ANIM_DURATION);
        mAnimTranY.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = animation.getAnimatedFraction();
                float animateY = fraction * diffY;
                child.setTranslationY(originTransY + animateY);
            }
        });
        mAnimTranY.setInterpolator(new DecelerateInterpolator());
        mAnimTranY.addListener(mFlingAnimatorListener);
    }

    private void performChildFlingHorizontalAnimationIfNeeded(float velocityX) {
        if (!mIsScaling) {
            final View child = child();
            final float originTransX = child.getTranslationX();
            final float dx = velocityX / PER_SECONDS * FLING_ANIM_DURATION;
            final float currentTransX = calculateTranslation(child, originTransX, dx, HORIZONTAL);
            if (originTransX == currentTransX) {
                return;
            }
            final float diffX = currentTransX - originTransX;
            mAnimTranX = ValueAnimator.ofFloat(originTransX, currentTransX)
                    .setDuration(FLING_ANIM_DURATION);
            mAnimTranX.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float fraction = animation.getAnimatedFraction();
                    float animateX = fraction * diffX;
                    child.setTranslationX(originTransX + animateX);
                }
            });
            mAnimTranX.addListener(mFlingAnimatorListener);
            mAnimTranX.start();
        }
    }

    private void performChildFlingVerticalAnimationIfNeeded(float velocityY) {
        if (!mIsScaling) {
            final View child = child();
            final float originTransY = child.getTranslationY();
            final float dy = velocityY / PER_SECONDS * FLING_ANIM_DURATION;
            final float currentTransY = calculateTranslation(child, originTransY, dy, VERTICAL);
            if (originTransY == currentTransY) {
                return;
            }
            final float diffY = currentTransY - originTransY;
            mAnimTranY = ValueAnimator.ofFloat(originTransY, currentTransY)
                    .setDuration(FLING_ANIM_DURATION);
            mAnimTranY.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float fraction = animation.getAnimatedFraction();
                    float animateY = fraction * diffY;
                    child.setTranslationY(originTransY + animateY);
                }
            });
            mAnimTranY.addListener(mFlingAnimatorListener);
            mAnimTranY.start();
        }
    }

    private void performChildScaleIfNeeded() {
        View child = child();
        if (checkScaleFactor(child.getScaleX(), child.getScaleY())) {
            return;
        }
        child.setScaleX(mScaleFactor);
        child.setScaleY(mScaleFactor);
    }

    private void performChildScaleWithAnimationIfNeeded() {
        View child = child();
        float originFactorX = child.getScaleX();
        float originFactorY = child.getScaleY();
        if (checkScaleFactor(originFactorX, originFactorY)) {
            return;
        }
        ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(child, ViewGroup.SCALE_X,
                originFactorX, mScaleFactor);
        ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(child, ViewGroup.SCALE_Y,
                originFactorY, mScaleFactor);
        AnimatorSet animSet = new AnimatorSet();
        animSet.setDuration(SCALE_ANIM_DURATION);
        animSet.setInterpolator(new AccelerateInterpolator());
        animSet.playTogether(scaleXAnimator, scaleYAnimator);
        animSet.start();
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        mScaleFactor *= detector.getScaleFactor();
        mScaleFactor = Math.max(MIN_SCALE_FACTOR, Math.min(mScaleFactor, MAX_SCALE_FACTOR));
        performChildScaleIfNeeded();
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        mIsScaling = true;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        mIsScaling = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
    }

    private View child() {
        return getChildAt(0);
    }

    private GestureDetector.SimpleOnGestureListener mSimpleOnGestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            mScaleFactor += INCREASE_SCALE_FACTOR;
            mScaleFactor = Math.max(MIN_SCALE_FACTOR, Math.min(mScaleFactor, MAX_SCALE_FACTOR));
            performChildScaleWithAnimationIfNeeded();
            return true;
        }

//        @Override
//        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
//            scrollBy((int) distanceX, (int) distanceY);
//            return super.onScroll(e1, e2, distanceX, distanceY);
//
//        }

//        @Override
//        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
//            int x = mScroller.getCurrX();
//            int y = mScroller.getCurrY();
//            int vX = (int) -velocityX;
//            int vY = (int) -velocityY;
//            int minX = (int) mScrollBound.left;
//            int maxX = (int) mScrollBound.right;
//            int minY = (int) mScrollBound.top;
//            int maxY = (int) mScrollBound.bottom;
//            mScroller.fling(x, y, vX, vY, minX, maxX, minY, maxY);
//            return super.onFling(e1, e2, velocityX, velocityY);
//        }
    };

//    @Override
//    public void computeScroll() {
//        if (mScroller.computeScrollOffset()) {
//            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
//            postInvalidate();
//        }
//    }

    private boolean checkScaleFactor(float originScaleFactorX, float originScaleFactorY) {
        boolean checkMaxFactor = (originScaleFactorX == MAX_SCALE_FACTOR && originScaleFactorY == MAX_SCALE_FACTOR
                && mScaleFactor >= MAX_SCALE_FACTOR);
        boolean checkMinFactor = (originScaleFactorX == MIN_SCALE_FACTOR && originScaleFactorY == MIN_SCALE_FACTOR
                && mScaleFactor <= MIN_SCALE_FACTOR);
        return checkMaxFactor || checkMinFactor;
    }

    private AnimatorListenerAdapter mFlingAnimatorListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationCancel(Animator animation) {
            super.onAnimationCancel(animation);
            mIsFlinging = false;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            mIsFlinging = false;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            super.onAnimationStart(animation);
            mIsFlinging = true;
        }

        @Override
        public void onAnimationPause(Animator animation) {
            super.onAnimationPause(animation);
            mIsFlinging = false;
        }
    };

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
        }
    }
}
