package io.github.SirWashington.block;

/** Immutable structure snapshot captured before chunk compilation. Animation is deliberately excluded. */
public record MachineryRenderData(int size, int column, int row, int connections) { }
