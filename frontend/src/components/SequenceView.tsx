import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import ReactMarkdown from "react-markdown";
import type { TieredTaskSequence } from "../types/models";

interface Props {
    sequence: TieredTaskSequence;
}

export const SequenceView: React.FC<Props> = ({ sequence }) => {
    const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
    const navigate = useNavigate();

    const tasks = [...sequence.tasks].sort((a, b) => a.tierLevel - b.tierLevel);
    const selectedTask = tasks.find((t) => t.id === selectedTaskId);

    return (
        <div>
            <h2 className="mb-3">Generated Tasks</h2>
            <div className="row">
                {tasks.map((task) => (
                    <div className="col-md-4 mb-3" key={task.id}>
                        <div
                            className={`card h-100 ${selectedTaskId === task.id ? "border-primary" : ""}`}
                            style={{ cursor: "pointer" }}
                            onClick={() => setSelectedTaskId(task.id)}
                        >
                            <div className="card-body">
                                <h5 className="card-title">{`Tier ${task.tierLevel}`}</h5>
                                <p className="card-text">
                                    {task.statement.length > 60
                                        ? task.statement.slice(0, 60) + "..."
                                        : task.statement}
                                </p>
                                <button
                                    className="btn btn-success mt-2"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        navigate(`/solve/${task.id}`, {
                                            state: { task, sequence },
                                        });
                                    }}
                                >
                                    Solve Parson's Problem
                                </button>
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            {selectedTask && (
                <div className="card mt-3">
                    <div className="card-body">
                        <h5 className="card-title">{`Tier ${selectedTask.tierLevel}`}</h5>
                        <b>Aufgabenstellung:</b>
                        <ReactMarkdown>{selectedTask.statement}</ReactMarkdown>
                    </div>
                </div>
            )}
        </div>
    );
};