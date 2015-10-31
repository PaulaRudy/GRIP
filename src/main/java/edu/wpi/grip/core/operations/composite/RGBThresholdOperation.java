package edu.wpi.grip.core.operations.composite;

import com.google.common.eventbus.EventBus;
import edu.wpi.grip.core.InputSocket;
import edu.wpi.grip.core.Operation;
import edu.wpi.grip.core.OutputSocket;
import edu.wpi.grip.core.SocketHint;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.bytedeco.javacpp.opencv_core.*;

/**
 * An {@link Operation} that converts a color image into a binary image based on threshold ranges for each channel
 */
public class RGBThresholdOperation implements Operation {

    private final SocketHint<Mat> inputHint = new SocketHint<Mat>("Input", Mat.class, Mat::new);
    private final SocketHint<List> redHint = new SocketHint("Red", List.class,
            () -> Arrays.asList(0.0, 255.0), SocketHint.View.RANGE, new Number[]{0.0, 255.0});
    private final SocketHint<List> greenHint = new SocketHint("Green", List.class,
            () -> Arrays.asList(0.0, 255.0), SocketHint.View.RANGE, new Number[]{0.0, 255.0});
    private final SocketHint<List> blueHint = new SocketHint("Blue", List.class,
            () -> Arrays.asList(0.0, 255.0), SocketHint.View.RANGE, new Number[]{0.0, 255.0});

    private final SocketHint<Mat> outputHint = new SocketHint<Mat>("output", Mat.class, Mat::new);

    @Override
    public String getName() {
        return "RGB Threshold";
    }

    @Override
    public String getDescription() {
        return "Segment an image based on color ranges";
    }

    @Override
    public Optional<InputStream> getIcon() {
        return Optional.of(getClass().getResourceAsStream("/edu/wpi/grip/ui/icons/threshold.png"));
    }

    @Override
    public InputSocket<?>[] createInputSockets(EventBus eventBus) {
        return new InputSocket<?>[]{
                new InputSocket<>(eventBus, inputHint),
                new InputSocket<>(eventBus, redHint),
                new InputSocket<>(eventBus, greenHint),
                new InputSocket<>(eventBus, blueHint),
        };
    }

    @Override
    public OutputSocket<?>[] createOutputSockets(EventBus eventBus) {
        return new OutputSocket<?>[]{
                new OutputSocket<>(eventBus, outputHint)
        };
    }

    final private Mat low = new Mat();
    final private Mat high = new Mat();

    @Override
    @SuppressWarnings("unchecked")
    public void perform(InputSocket<?>[] inputs, OutputSocket<?>[] outputs) {
        final Mat input = ((InputSocket<Mat>) inputs[0]).getValue();
        final List<Number> channel1 = ((InputSocket<List<Number>>) inputs[1]).getValue();
        final List<Number> channel2 = ((InputSocket<List<Number>>) inputs[2]).getValue();
        final List<Number> channel3 = ((InputSocket<List<Number>>) inputs[3]).getValue();

        final OutputSocket<Mat> outputSocket = (OutputSocket<Mat>) outputs[0];
        final Mat output = outputSocket.getValue();

        // Do nothing if nothing is connected to the input
        if (input.empty()) {
            return;
        }

        final Mat low = new Mat(input.size(), input.type(), new Scalar(
                channel3.get(0).doubleValue(),
                channel2.get(0).doubleValue(),
                channel1.get(0).doubleValue(), 0));

        final Mat high = new Mat(input.size(), input.type(), new Scalar(
                channel3.get(1).doubleValue(),
                channel2.get(1).doubleValue(),
                channel1.get(1).doubleValue(), 0));

        inRange(input, low, high, output);

        outputSocket.setValue(output);
    }
}
