package com.shareverance.dto;

public enum SplitMode {
    INDIVIDUAL,         // Con nadie (100% titular)
    BETWEEN_TWO,        // Entre dos (titular y otro específico, o custom)
    SPECIFIC_GROUP,     // Grupo específico (partes iguales entre splitMemberIds)
    ALL_MEMBERS,        // Con todos los miembros activos de la organización en partes iguales
    CUSTOM              // Detallado explícitamente en customSplits
}

