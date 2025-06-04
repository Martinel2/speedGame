package com.example.term

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLitePredictor(context: Context) {

    private val interpreter: Interpreter

    init {
        val model = loadModelFile(context, "reaction_model.tflite")
        interpreter = Interpreter(model)
    }

    fun predictReactionAge(input: FloatArray): String {
        // 입력: 평균 반응속도 1개 → 출력: softmax 확률 5개
        val output = Array(1) { FloatArray(5) }
        interpreter.run(arrayOf(input), output)

        val probabilities = output[0]
        val predictedIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0

        return mapToAge(predictedIndex)
    }

    private fun mapToAge(value: Int): String {
        return when (value) {
            0 -> "10세 이하"
            1 -> "10대 중반"
            2 -> "20대 중반"
            3 -> "30대 후반"
            else -> "40대 이상"
        }
    }


    private fun loadModelFile(context: Context, fileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}
