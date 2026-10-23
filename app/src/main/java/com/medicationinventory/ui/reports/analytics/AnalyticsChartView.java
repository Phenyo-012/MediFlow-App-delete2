package com.medicationinventory.ui.reports.analytics;


import android.content.Context;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import android.util.AttributeSet;

import android.view.View;

import androidx.annotation.Nullable;

import com.medicationinventory.model.domain.AnalyticsReport;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;



/**
 * Implements the Analytics Chart View behavior for its Android screen and the application state it presents.
 */
public class AnalyticsChartView
        extends View
{
    public enum Mode
    {
        DONUT,
        VERTICAL_BAR,
        MOVEMENT_LINE,
        HORIZONTAL_BAR
    }


    private static final int[] COLORS =
            {
                    Color.rgb(10, 146, 154),
                    Color.rgb(9, 50, 93),
                    Color.rgb(245, 158, 11),
                    Color.rgb(214, 69, 65),
                    Color.rgb(122, 102, 201),
                    Color.rgb(47, 158, 100),
                    Color.rgb(219, 98, 140),
                    Color.rgb(95, 110, 124)
            };


    private static final int TEXT_PRIMARY =
            Color.rgb(
                    23,
                    32,
                    51
            );


    private static final int TEXT_SECONDARY =
            Color.rgb(
                    115,
                    128,
                    150
            );


    private static final int GRID_COLOR =
            Color.rgb(
                    225,
                    229,
                    235
            );


    private final Paint fillPaint =
            new Paint(
                    Paint.ANTI_ALIAS_FLAG
            );


    private final Paint strokePaint =
            new Paint(
                    Paint.ANTI_ALIAS_FLAG
            );


    private final Paint textPaint =
            new Paint(
                    Paint.ANTI_ALIAS_FLAG
            );


    private final Paint gridPaint =
            new Paint(
                    Paint.ANTI_ALIAS_FLAG
            );


    private final NumberFormat numberFormat =
            NumberFormat
                    .getIntegerInstance(
                            Locale.getDefault()
                    );


    private final SimpleDateFormat shortDateFormat =
            new SimpleDateFormat(
                    "dd MMM",
                    Locale.getDefault()
            );


    private Mode mode =
            Mode.DONUT;


    private List<AnalyticsReport.DistributionItem>
            distributionItems =
            Collections.emptyList();


    private List<AnalyticsReport.MovementTrendPoint>
            movementTrendPoints =
            Collections.emptyList();


    private List<AnalyticsReport.TopMedicineItem>
            topMedicineItems =
            Collections.emptyList();


    private String donutCenterLabel =
            "";


    private int maxDonutSegments =
            0;


    public AnalyticsChartView(
            Context context)
    {
        super(
                context
        );

        initialise();
    }


    public AnalyticsChartView(
            Context context,
            @Nullable AttributeSet attrs)
    {
        super(
                context,
                attrs
        );

        initialise();
    }


    public AnalyticsChartView(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr)
    {
        super(
                context,
                attrs,
                defStyleAttr
        );

        initialise();
    }


    private void initialise()
    {
        setImportantForAccessibility(
                IMPORTANT_FOR_ACCESSIBILITY_YES
        );


        gridPaint.setColor(
                GRID_COLOR
        );


        gridPaint.setStrokeWidth(
                dp(
                        1f
                )
        );


        strokePaint.setStyle(
                Paint.Style.STROKE
        );


        strokePaint.setStrokeCap(
                Paint.Cap.ROUND
        );
    }


    public void setDistributionData(
            List<AnalyticsReport.DistributionItem> items,
            Mode chartMode,
            String centerLabel,
            int maximumDonutSegments)
    {
        if (chartMode != Mode.DONUT
                &&
                chartMode != Mode.VERTICAL_BAR)
        {
            throw new IllegalArgumentException(
                    "Distribution data supports DONUT or VERTICAL_BAR charts."
            );
        }


        mode =
                chartMode;


        distributionItems =
                safeDistributionCopy(
                        items
                );


        movementTrendPoints =
                Collections.emptyList();


        topMedicineItems =
                Collections.emptyList();


        donutCenterLabel =
                centerLabel == null
                        ? ""
                        : centerLabel;


        maxDonutSegments =
                Math.max(
                        maximumDonutSegments,
                        0
                );


        setContentDescription(
                buildDistributionDescription(
                        distributionItems
                )
        );


        invalidate();
    }


    public void setMovementTrendData(
            List<AnalyticsReport.MovementTrendPoint> points)
    {
        mode =
                Mode.MOVEMENT_LINE;


        movementTrendPoints =
                points == null
                        ? Collections.emptyList()
                        : new ArrayList<>(
                        points
                );


        distributionItems =
                Collections.emptyList();


        topMedicineItems =
                Collections.emptyList();


        setContentDescription(
                buildMovementTrendDescription(
                        movementTrendPoints
                )
        );


        invalidate();
    }


    public void setTopIssuedData(
            List<AnalyticsReport.TopMedicineItem> items)
    {
        mode =
                Mode.HORIZONTAL_BAR;


        topMedicineItems =
                items == null
                        ? Collections.emptyList()
                        : new ArrayList<>(
                        items
                );


        distributionItems =
                Collections.emptyList();


        movementTrendPoints =
                Collections.emptyList();


        setContentDescription(
                buildTopIssuedDescription(
                        topMedicineItems
                )
        );


        invalidate();
    }


    @Override

    protected void onDraw(
            Canvas canvas)
    {
        super.onDraw(
                canvas
        );


        switch (mode)
        {
            case DONUT:

                drawDonutChart(
                        canvas
                );

                break;


            case VERTICAL_BAR:

                drawVerticalBarChart(
                        canvas
                );

                break;


            case MOVEMENT_LINE:

                drawMovementLineChart(
                        canvas
                );

                break;


            case HORIZONTAL_BAR:

                drawHorizontalBarChart(
                        canvas
                );

                break;
        }
    }



    private void drawDonutChart(
            Canvas canvas)
    {
        List<AnalyticsReport.DistributionItem>
                values =
                prepareDonutValues();


        long total =
                totalDistributionValue(
                        values
                );


        if (values.isEmpty()
                ||
                total <= 0L)
        {
            return;
        }


        float width =
                getWidth();


        float height =
                getHeight();


        float padding =
                dp(
                        14f
                );


        float legendStartX =
                width
                        *
                        0.50f;


        float availableCircleWidth =
                legendStartX
                        -
                        padding
                                *
                                2f;


        float diameter =
                Math.min(
                        availableCircleWidth,
                        height
                                -
                                dp(
                                        28f
                                )
                );


        diameter =
                Math.max(
                        diameter,
                        dp(
                                90f
                        )
                );


        float centerX =
                padding
                        +
                        diameter
                                /
                                2f;


        float centerY =
                height
                        /
                        2f;


        float outerRadius =
                diameter
                        /
                        2f;


        float strokeWidth =
                Math.max(
                        dp(
                                16f
                        ),
                        outerRadius
                                *
                                0.28f
                );


        float radius =
                outerRadius
                        -
                        strokeWidth
                                /
                                2f;


        RectF oval =
                new RectF(
                        centerX - radius,
                        centerY - radius,
                        centerX + radius,
                        centerY + radius
                );


        strokePaint.setStyle(
                Paint.Style.STROKE
        );


        strokePaint.setStrokeWidth(
                strokeWidth
        );


        strokePaint.setStrokeCap(
                Paint.Cap.BUTT
        );


        float startAngle =
                -90f;


        for (int index = 0;
             index < values.size();
             index++)
        {
            AnalyticsReport.DistributionItem item =
                    values.get(
                            index
                    );


            float sweepAngle =
                    360f
                            *
                            (
                                    (float)
                                            item.getValue()
                                            /
                                            (float)
                                                    total
                            );


            strokePaint.setColor(
                    colorFor(
                            item,
                            index
                    )
            );


            canvas.drawArc(
                    oval,
                    startAngle,
                    sweepAngle,
                    false,
                    strokePaint
            );


            startAngle +=
                    sweepAngle;
        }


        /*
         * Center total.
         */
        textPaint.setTextAlign(
                Paint.Align.CENTER
        );


        textPaint.setColor(
                TEXT_PRIMARY
        );


        textPaint.setTypeface(
                android.graphics.Typeface.DEFAULT_BOLD
        );


        textPaint.setTextSize(
                sp(
                        20f
                )
        );


        canvas.drawText(
                numberFormat.format(
                        total
                ),
                centerX,
                centerY,
                textPaint
        );


        textPaint.setColor(
                TEXT_SECONDARY
        );


        textPaint.setTypeface(
                android.graphics.Typeface.DEFAULT
        );


        textPaint.setTextSize(
                sp(
                        10f
                )
        );


        canvas.drawText(
                donutCenterLabel,
                centerX,
                centerY
                        +
                        dp(
                                18f
                        ),
                textPaint
        );


        /*
         * Legend.
         */
        float legendTop =
                dp(
                        12f
                );


        float legendBottom =
                height
                        -
                        dp(
                                12f
                        );


        float legendHeight =
                legendBottom
                        -
                        legendTop;


        float rowHeight =
                legendHeight
                        /
                        values.size();


        rowHeight =
                Math.min(
                        rowHeight,
                        dp(
                                32f
                        )
                );


        float actualLegendHeight =
                rowHeight
                        *
                        values.size();


        float rowY =
                (
                        height
                                -
                                actualLegendHeight
                )
                        /
                        2f;


        textPaint.setTextAlign(
                Paint.Align.LEFT
        );


        textPaint.setTypeface(
                android.graphics.Typeface.DEFAULT
        );


        textPaint.setTextSize(
                sp(
                        values.size() >= 7
                                ? 9.5f
                                : 10.5f
                )
        );


        for (int index = 0;
             index < values.size();
             index++)
        {
            AnalyticsReport.DistributionItem item =
                    values.get(
                            index
                    );


            float centerRowY =
                    rowY
                            +
                            rowHeight
                                    /
                                    2f;


            float swatchSize =
                    dp(
                            9f
                    );


            fillPaint.setColor(
                    colorFor(
                            item,
                            index
                    )
            );


            canvas.drawRect(
                    legendStartX,
                    centerRowY
                            -
                            swatchSize
                                    /
                                    2f,
                    legendStartX
                            +
                            swatchSize,
                    centerRowY
                            +
                            swatchSize
                                    /
                                    2f,
                    fillPaint
            );


            float percentage =
                    (
                            item.getValue()
                                    *
                                    100f
                    )
                            /
                            total;


            String valueText =
                    item.getLabel()
                            +
                            "  "
                            +
                            numberFormat.format(
                                    item.getValue()
                            )
                            +
                            " ("
                            +
                            Math.round(
                                    percentage
                            )
                            +
                            "%)";


            textPaint.setColor(
                    TEXT_PRIMARY
            );


            float availableWidth =
                    width
                            -
                            (
                                    legendStartX
                                            +
                                            swatchSize
                                            +
                                            dp(
                                                    10f
                                            )
                            )
                            -
                            padding;


            String displayed =
                    fitText(
                            valueText,
                            availableWidth,
                            textPaint
                    );


            Paint.FontMetrics metrics =
                    textPaint
                            .getFontMetrics();


            float baseline =
                    centerRowY
                            -
                            (
                                    metrics.ascent
                                            +
                                            metrics.descent
                            )
                                    /
                                    2f;


            canvas.drawText(
                    displayed,
                    legendStartX
                            +
                            swatchSize
                            +
                            dp(
                                    7f
                            ),
                    baseline,
                    textPaint
            );


            rowY +=
                    rowHeight;
        }
    }



    private void drawVerticalBarChart(
            Canvas canvas)
    {
        if (distributionItems.isEmpty())
        {
            return;
        }


        long maximum =
                0L;


        for (AnalyticsReport.DistributionItem item :
                distributionItems)
        {
            if (item != null)
            {
                maximum =
                        Math.max(
                                maximum,
                                item.getValue()
                        );
            }
        }


        if (maximum <= 0L)
        {
            return;
        }


        float width =
                getWidth();


        float height =
                getHeight();


        float left =
                dp(
                        44f
                );


        float right =
                width
                        -
                        dp(
                                10f
                        );


        float top =
                dp(
                        30f
                );


        float bottom =
                height
                        -
                        dp(
                                50f
                        );


        float chartHeight =
                bottom
                        -
                        top;


        float chartWidth =
                right
                        -
                        left;


        drawYAxisGrid(
                canvas,
                left,
                right,
                top,
                bottom,
                maximum
        );


        int count =
                distributionItems.size();


        float slotWidth =
                chartWidth
                        /
                        count;


        float barWidth =
                Math.min(
                        slotWidth
                                *
                                0.58f,
                        dp(
                                52f
                        )
                );


        for (int index = 0;
             index < count;
             index++)
        {
            AnalyticsReport.DistributionItem item =
                    distributionItems.get(
                            index
                    );


            if (item == null)
            {
                continue;
            }


            float centerX =
                    left
                            +
                            slotWidth
                                    *
                                    index
                            +
                            slotWidth
                                    /
                                    2f;


            float ratio =
                    (float)
                            item.getValue()
                            /
                            (float)
                                    maximum;


            float barHeight =
                    chartHeight
                            *
                            ratio;


            float barTop =
                    bottom
                            -
                            barHeight;


            fillPaint.setColor(
                    colorFor(
                            item,
                            index
                    )
            );


            RectF bar =
                    new RectF(
                            centerX
                                    -
                                    barWidth
                                            /
                                            2f,
                            barTop,
                            centerX
                                    +
                                    barWidth
                                            /
                                            2f,
                            bottom
                    );


            canvas.drawRoundRect(
                    bar,
                    dp(
                            5f
                    ),
                    dp(
                            5f
                    ),
                    fillPaint
            );


            textPaint.setColor(
                    TEXT_PRIMARY
            );


            textPaint.setTextAlign(
                    Paint.Align.CENTER
            );


            textPaint.setTypeface(
                    android.graphics.Typeface.DEFAULT_BOLD
            );


            textPaint.setTextSize(
                    sp(
                            9f
                    )
            );


            canvas.drawText(
                    numberFormat.format(
                            item.getValue()
                    ),
                    centerX,
                    Math.max(
                            top
                                    +
                                    sp(
                                            9f
                                    ),
                            barTop
                                    -
                                    dp(
                                            5f
                                    )
                    ),
                    textPaint
            );


            textPaint.setTypeface(
                    android.graphics.Typeface.DEFAULT
            );


            textPaint.setColor(
                    TEXT_SECONDARY
            );


            textPaint.setTextSize(
                    sp(
                            9f
                    )
            );


            String label =
                    item.getLabel()
                            .replace(
                                    " days",
                                    ""
                            );


            label =
                    fitText(
                            label,
                            slotWidth
                                    -
                                    dp(
                                            4f
                                    ),
                            textPaint
                    );


            canvas.drawText(
                    label,
                    centerX,
                    bottom
                            +
                            dp(
                                    20f
                            ),
                    textPaint
            );
        }
    }



    private void drawMovementLineChart(
            Canvas canvas)
    {
        if (movementTrendPoints.isEmpty())
        {
            return;
        }


        long maximum =
                0L;


        for (AnalyticsReport.MovementTrendPoint point :
                movementTrendPoints)
        {
            if (point == null)
            {
                continue;
            }


            maximum =
                    Math.max(
                            maximum,
                            Math.max(
                                    point.getReceivedQuantity(),
                                    point.getIssuedQuantity()
                            )
                    );
        }


        if (maximum <= 0L)
        {
            return;
        }


        float width =
                getWidth();


        float height =
                getHeight();


        float left =
                dp(
                        44f
                );


        float right =
                width
                        -
                        dp(
                                12f
                        );


        float top =
                dp(
                        42f
                );


        float bottom =
                height
                        -
                        dp(
                                42f
                        );


        float chartWidth =
                right
                        -
                        left;


        float chartHeight =
                bottom
                        -
                        top;


        drawYAxisGrid(
                canvas,
                left,
                right,
                top,
                bottom,
                maximum
        );


        /*
         * Legend.
         */
        drawLineLegend(
                canvas
        );


        Path receivedPath =
                new Path();


        Path issuedPath =
                new Path();


        int count =
                movementTrendPoints.size();


        for (int index = 0;
             index < count;
             index++)
        {
            AnalyticsReport.MovementTrendPoint point =
                    movementTrendPoints.get(
                            index
                    );


            float x;


            if (count == 1)
            {
                x =
                        left
                                +
                                chartWidth
                                        /
                                        2f;
            }
            else
            {
                x =
                        left
                                +
                                chartWidth
                                        *
                                        (
                                                (float)
                                                        index
                                                        /
                                                        (float)
                                                                (
                                                                        count
                                                                                -
                                                                                1
                                                                )
                                        );
            }


            float receivedY =
                    bottom
                            -
                            chartHeight
                                    *
                                    (
                                            (float)
                                                    point.getReceivedQuantity()
                                                    /
                                                    (float)
                                                            maximum
                                    );


            float issuedY =
                    bottom
                            -
                            chartHeight
                                    *
                                    (
                                            (float)
                                                    point.getIssuedQuantity()
                                                    /
                                                    (float)
                                                            maximum
                                    );


            if (index == 0)
            {
                receivedPath.moveTo(
                        x,
                        receivedY
                );


                issuedPath.moveTo(
                        x,
                        issuedY
                );
            }
            else
            {
                receivedPath.lineTo(
                        x,
                        receivedY
                );


                issuedPath.lineTo(
                        x,
                        issuedY
                );
            }
        }


        strokePaint.setStyle(
                Paint.Style.STROKE
        );


        strokePaint.setStrokeWidth(
                dp(
                        2.4f
                )
        );


        strokePaint.setStrokeCap(
                Paint.Cap.ROUND
        );


        strokePaint.setColor(
                COLORS[1]
        );


        canvas.drawPath(
                receivedPath,
                strokePaint
        );


        strokePaint.setColor(
                COLORS[0]
        );


        canvas.drawPath(
                issuedPath,
                strokePaint
        );



        if (count <= 30)
        {
            for (int index = 0;
                 index < count;
                 index++)
            {
                AnalyticsReport.MovementTrendPoint point =
                        movementTrendPoints.get(
                                index
                        );


                float x =
                        count == 1
                                ?
                                left
                                +
                                chartWidth
                                /
                                2f
                                :
                                left
                                +
                                chartWidth
                                *
                                (
                                        (float)
                                        index
                                        /
                                        (
                                                count
                                                -
                                                1f
                                        )
                                );


                float receivedY =
                        bottom
                                -
                                chartHeight
                                        *
                                        (
                                                (float)
                                                        point.getReceivedQuantity()
                                                        /
                                                        maximum
                                        );


                float issuedY =
                        bottom
                                -
                                chartHeight
                                        *
                                        (
                                                (float)
                                                        point.getIssuedQuantity()
                                                        /
                                                        maximum
                                        );


                fillPaint.setColor(
                        COLORS[1]
                );


                canvas.drawCircle(
                        x,
                        receivedY,
                        dp(
                                2.6f
                        ),
                        fillPaint
                );


                fillPaint.setColor(
                        COLORS[0]
                );


                canvas.drawCircle(
                        x,
                        issuedY,
                        dp(
                                2.6f
                        ),
                        fillPaint
                );
            }
        }


        drawMovementDateLabels(
                canvas,
                left,
                right,
                bottom
        );
    }



    private void drawHorizontalBarChart(
            Canvas canvas)
    {
        if (topMedicineItems.isEmpty())
        {
            return;
        }


        long maximum =
                0L;


        for (AnalyticsReport.TopMedicineItem item :
                topMedicineItems)
        {
            if (item != null)
            {
                maximum =
                        Math.max(
                                maximum,
                                item.getQuantityIssued()
                        );
            }
        }


        if (maximum <= 0L)
        {
            return;
        }


        float width =
                getWidth();


        float height =
                getHeight();


        float top =
                dp(
                        8f
                );


        float bottom =
                height
                        -
                        dp(
                                8f
                        );


        float labelWidth =
                Math.min(
                        width
                                *
                                0.40f,
                        dp(
                                130f
                        )
                );


        float barStart =
                labelWidth
                        +
                        dp(
                                8f
                        );


        float barEnd =
                width
                        -
                        dp(
                                50f
                        );


        float availableBarWidth =
                Math.max(
                        dp(
                                40f
                        ),
                        barEnd
                                -
                                barStart
                );


        float rowHeight =
                (
                        bottom
                                -
                                top
                )
                        /
                        topMedicineItems.size();


        for (int index = 0;
             index < topMedicineItems.size();
             index++)
        {
            AnalyticsReport.TopMedicineItem item =
                    topMedicineItems.get(
                            index
                    );


            if (item == null)
            {
                continue;
            }


            float centerY =
                    top
                            +
                            rowHeight
                                    *
                                    index
                            +
                            rowHeight
                                    /
                                    2f;


            float ratio =
                    (float)
                            item.getQuantityIssued()
                            /
                            (float)
                                    maximum;


            float barWidth =
                    availableBarWidth
                            *
                            ratio;


            float barHeight =
                    Math.min(
                            rowHeight
                                    *
                                    0.48f,
                            dp(
                                    24f
                            )
                    );


            String medicineLabel =
                    item.getMedicineName();


            if (item.getMedicineCode() != null
                    &&
                    !item.getMedicineCode()
                            .trim()
                            .isEmpty())
            {
                medicineLabel =
                        medicineLabel
                                +
                                " ("
                                +
                                item.getMedicineCode()
                                +
                                ")";
            }


            textPaint.setColor(
                    TEXT_PRIMARY
            );


            textPaint.setTextAlign(
                    Paint.Align.LEFT
            );


            textPaint.setTypeface(
                    android.graphics.Typeface.DEFAULT
            );


            textPaint.setTextSize(
                    sp(
                            9.5f
                    )
            );


            medicineLabel =
                    fitText(
                            medicineLabel,
                            labelWidth
                                    -
                                    dp(
                                            8f
                                    ),
                            textPaint
                    );


            Paint.FontMetrics metrics =
                    textPaint
                            .getFontMetrics();


            float baseline =
                    centerY
                            -
                            (
                                    metrics.ascent
                                            +
                                            metrics.descent
                            )
                                    /
                                    2f;


            canvas.drawText(
                    medicineLabel,
                    dp(
                            2f
                    ),
                    baseline,
                    textPaint
            );


            fillPaint.setColor(
                    COLORS[
                            index
                                    %
                                    COLORS.length
                            ]
            );


            RectF bar =
                    new RectF(
                            barStart,
                            centerY
                                    -
                                    barHeight
                                            /
                                            2f,
                            barStart
                                    +
                                    barWidth,
                            centerY
                                    +
                                    barHeight
                                            /
                                            2f
                    );


            canvas.drawRoundRect(
                    bar,
                    dp(
                            5f
                    ),
                    dp(
                            5f
                    ),
                    fillPaint
            );


            textPaint.setColor(
                    TEXT_PRIMARY
            );


            textPaint.setTypeface(
                    android.graphics.Typeface.DEFAULT_BOLD
            );


            textPaint.setTextSize(
                    sp(
                            9f
                    )
            );


            canvas.drawText(
                    numberFormat.format(
                            item.getQuantityIssued()
                    ),
                    barEnd
                            +
                            dp(
                                    5f
                            ),
                    baseline,
                    textPaint
            );
        }
    }


    private void drawYAxisGrid(
            Canvas canvas,
            float left,
            float right,
            float top,
            float bottom,
            long maximum)
    {
        final int divisions =
                4;


        textPaint.setTypeface(
                android.graphics.Typeface.DEFAULT
        );


        textPaint.setTextAlign(
                Paint.Align.RIGHT
        );


        textPaint.setTextSize(
                sp(
                        8.5f
                )
        );


        textPaint.setColor(
                TEXT_SECONDARY
        );


        for (int index = 0;
             index <= divisions;
             index++)
        {
            float ratio =
                    (float)
                            index
                            /
                            divisions;


            float y =
                    bottom
                            -
                            (
                                    bottom
                                            -
                                            top
                            )
                                    *
                                    ratio;


            canvas.drawLine(
                    left,
                    y,
                    right,
                    y,
                    gridPaint
            );


            long labelValue =
                    Math.round(
                            maximum
                                    *
                                    ratio
                    );


            canvas.drawText(
                    numberFormat.format(
                            labelValue
                    ),
                    left
                            -
                            dp(
                                    6f
                            ),
                    y
                            +
                            dp(
                                    3f
                            ),
                    textPaint
            );
        }
    }


    private void drawLineLegend(
            Canvas canvas)
    {
        float startX =
                dp(
                        48f
                );


        float y =
                dp(
                        17f
                );


        strokePaint.setStrokeWidth(
                dp(
                        3f
                )
        );


        strokePaint.setColor(
                COLORS[1]
        );


        canvas.drawLine(
                startX,
                y,
                startX
                        +
                        dp(
                                18f
                        ),
                y,
                strokePaint
        );


        textPaint.setColor(
                TEXT_PRIMARY
        );


        textPaint.setTextSize(
                sp(
                        9f
                )
        );


        textPaint.setTypeface(
                android.graphics.Typeface.DEFAULT
        );


        textPaint.setTextAlign(
                Paint.Align.LEFT
        );


        canvas.drawText(
                "Received",
                startX
                        +
                        dp(
                                24f
                        ),
                y
                        +
                        dp(
                                3f
                        ),
                textPaint
        );


        float secondX =
                startX
                        +
                        dp(
                                100f
                        );


        strokePaint.setColor(
                COLORS[0]
        );


        canvas.drawLine(
                secondX,
                y,
                secondX
                        +
                        dp(
                                18f
                        ),
                y,
                strokePaint
        );


        canvas.drawText(
                "Issued",
                secondX
                        +
                        dp(
                                24f
                        ),
                y
                        +
                        dp(
                                3f
                        ),
                textPaint
        );
    }


    private void drawMovementDateLabels(
            Canvas canvas,
            float left,
            float right,
            float bottom)
    {
        int count =
                movementTrendPoints.size();


        if (count == 0)
        {
            return;
        }


        Set<Integer> indexes =
                new LinkedHashSet<>();


        if (count <= 5)
        {
            for (int index = 0;
                 index < count;
                 index++)
            {
                indexes.add(
                        index
                );
            }
        }
        else
        {
            indexes.add(
                    0
            );

            indexes.add(
                    Math.round(
                            (
                                    count
                                            -
                                            1
                            )
                                    *
                                    0.25f
                    )
            );

            indexes.add(
                    Math.round(
                            (
                                    count
                                            -
                                            1
                            )
                                    *
                                    0.50f
                    )
            );

            indexes.add(
                    Math.round(
                            (
                                    count
                                            -
                                            1
                            )
                                    *
                                    0.75f
                    )
            );

            indexes.add(
                    count
                            -
                            1
            );
        }


        float chartWidth =
                right
                        -
                        left;


        textPaint.setColor(
                TEXT_SECONDARY
        );


        textPaint.setTypeface(
                android.graphics.Typeface.DEFAULT
        );


        textPaint.setTextAlign(
                Paint.Align.CENTER
        );


        textPaint.setTextSize(
                sp(
                        8f
                )
        );


        for (Integer index :
                indexes)
        {
            AnalyticsReport.MovementTrendPoint point =
                    movementTrendPoints.get(
                            index
                    );


            float x;


            if (count == 1)
            {
                x =
                        left
                                +
                                chartWidth
                                        /
                                        2f;
            }
            else
            {
                x =
                        left
                                +
                                chartWidth
                                        *
                                        (
                                                (float)
                                                        index
                                                        /
                                                        (
                                                                count
                                                                        -
                                                                        1f
                                                        )
                                        );
            }


            String date =
                    shortDateFormat.format(
                            new Date(
                                    point.getDayStartMillis()
                            )
                    );


            canvas.drawText(
                    date,
                    x,
                    bottom
                            +
                            dp(
                                    18f
                            ),
                    textPaint
            );
        }
    }


    /*
     * DATA PREPARATION
     */
    private List<AnalyticsReport.DistributionItem>
    prepareDonutValues()
    {
        List<AnalyticsReport.DistributionItem>
                positive =
                new ArrayList<>();


        for (AnalyticsReport.DistributionItem item :
                distributionItems)
        {
            if (item != null
                    &&
                    item.getValue() > 0L)
            {
                positive.add(
                        item
                );
            }
        }


        if (maxDonutSegments <= 0
                ||
                positive.size()
                        <=
                        maxDonutSegments)
        {
            return positive;
        }


        int directCount =
                Math.max(
                        1,
                        maxDonutSegments
                                -
                                1
                );


        List<AnalyticsReport.DistributionItem>
                result =
                new ArrayList<>();


        long remainder =
                0L;


        for (int index = 0;
             index < positive.size();
             index++)
        {
            AnalyticsReport.DistributionItem item =
                    positive.get(
                            index
                    );


            if (index < directCount)
            {
                result.add(
                        item
                );
            }
            else
            {
                remainder +=
                        item.getValue();
            }
        }


        if (remainder > 0L)
        {
            result.add(
                    new AnalyticsReport
                            .DistributionItem(
                            "Other categories",
                            remainder
                    )
            );
        }


        return result;
    }


    private List<AnalyticsReport.DistributionItem>
    safeDistributionCopy(
            List<AnalyticsReport.DistributionItem> source)
    {
        if (source == null)
        {
            return Collections.emptyList();
        }


        return new ArrayList<>(
                source
        );
    }


    private long totalDistributionValue(
            List<AnalyticsReport.DistributionItem> values)
    {
        long total =
                0L;


        for (AnalyticsReport.DistributionItem item :
                values)
        {
            if (item != null)
            {
                total +=
                        Math.max(
                                0L,
                                item.getValue()
                        );
            }
        }


        return total;
    }


    private String buildDistributionDescription(
            List<AnalyticsReport.DistributionItem> values)
    {
        StringBuilder builder =
                new StringBuilder(
                        "Analytics chart. "
                );


        if (values == null
                ||
                values.isEmpty())
        {
            return builder
                    .append(
                            "No data."
                    )
                    .toString();
        }


        for (AnalyticsReport.DistributionItem item :
                values)
        {
            if (item == null)
            {
                continue;
            }


            builder
                    .append(
                            item.getLabel()
                    )
                    .append(
                            ": "
                    )
                    .append(
                            numberFormat.format(
                                    item.getValue()
                            )
                    )
                    .append(
                            ". "
                    );
        }


        return builder.toString();
    }


    private String buildMovementTrendDescription(
            List<AnalyticsReport.MovementTrendPoint> points)
    {
        long received =
                0L;


        long issued =
                0L;


        int activeDays =
                0;


        if (points != null)
        {
            for (AnalyticsReport.MovementTrendPoint point :
                    points)
            {
                if (point == null)
                {
                    continue;
                }


                received +=
                        point.getReceivedQuantity();


                issued +=
                        point.getIssuedQuantity();


                if (point.getReceivedQuantity() > 0L
                        ||
                        point.getIssuedQuantity() > 0L)
                {
                    activeDays++;
                }
            }
        }


        return "Movement trend chart. Received "
                +
                numberFormat.format(
                        received
                )
                +
                " units. Issued "
                +
                numberFormat.format(
                        issued
                )
                +
                " units. "
                +
                activeDays
                +
                " days contain recorded movement.";
    }


    private String buildTopIssuedDescription(
            List<AnalyticsReport.TopMedicineItem> items)
    {
        StringBuilder builder =
                new StringBuilder(
                        "Most issued medicines chart. "
                );


        if (items == null
                ||
                items.isEmpty())
        {
            return builder
                    .append(
                            "No issued stock."
                    )
                    .toString();
        }


        for (AnalyticsReport.TopMedicineItem item :
                items)
        {
            if (item == null)
            {
                continue;
            }


            builder
                    .append(
                            item.getMedicineName()
                    )
                    .append(
                            ": "
                    )
                    .append(
                            numberFormat.format(
                                    item.getQuantityIssued()
                            )
                    )
                    .append(
                            " units. "
                    );
        }


        return builder.toString();
    }


    private String fitText(
            String value,
            float maximumWidth,
            Paint paint)
    {
        if (value == null)
        {
            return "";
        }


        if (maximumWidth <= 0f)
        {
            return "";
        }


        if (paint.measureText(
                value
        )
                <=
                maximumWidth)
        {
            return value;
        }


        String ellipsis =
                "…";


        int end =
                value.length();


        while (end > 1)
        {
            String candidate =
                    value.substring(
                            0,
                            end
                    )
                            +
                            ellipsis;


            if (paint.measureText(
                    candidate
            )
                    <=
                    maximumWidth)
            {
                return candidate;
            }


            end--;
        }


        return ellipsis;
    }


    private int colorFor(
            AnalyticsReport.DistributionItem item,
            int index)
    {
        String label =
                item == null || item.getLabel() == null
                        ? ""
                        : item.getLabel()
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );


        switch (label)
        {
            case "in stock":

                return Color.rgb(
                        22,
                        163,
                        74
                );


            case "low stock":

                return Color.rgb(
                        245,
                        158,
                        11
                );


            case "out of stock":

                return Color.rgb(
                        214,
                        69,
                        65
                );


            case "expired":

                return Color.rgb(
                        155,
                        28,
                        28
                );


            default:

                return COLORS[
                        index
                                %
                                COLORS.length
                        ];
        }
    }


    private float dp(
            float value)
    {
        return value
                *
                getResources()
                        .getDisplayMetrics()
                        .density;
    }


    private float sp(
            float value)
    {
        return value
                *
                getResources()
                        .getDisplayMetrics()
                        .scaledDensity;
    }
}